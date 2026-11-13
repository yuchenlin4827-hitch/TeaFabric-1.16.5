// TeaFabric on Cloudflare Workers: serves build/web (static assets) to signed-in visitors only, and relays
// WebSocket connections to Minecraft servers (TCP) for multiplayer.
//
// The page contains Minecraft's code and assets, which must not be published: every request needs the
// password (HTTP Basic auth, any user name; the AUTH_PASSWORD secret). A successful sign-in also sets a
// signed cookie, which WebSocket connections to /relay carry.
//
// Multiplayer: in the game, connect to  wss://<this host>/relay?target=mc.example.com:25565
//
// /download/ offers the game as a zip (scripts/package-desktop.sh). It is over the 25 MiB file limit,
// so it is stored in parts (download/parts/manifest.json) that serveSplit() joins into one download.
import { connect } from "cloudflare:sockets";

const COOKIE = "tf_auth";
const encoder = new TextEncoder();

export default {
  async fetch(request, env) {
    if (!env.AUTH_PASSWORD) {
      return new Response("Not configured: set the AUTH_PASSWORD secret (wrangler secret put AUTH_PASSWORD).", {
        status: 503,
        headers: { "Cache-Control": "no-store" },
      });
    }
    const url = new URL(request.url);
    const token = await authToken(env.AUTH_PASSWORD);
    const viaPassword = await basicAuthOk(request, env.AUTH_PASSWORD);
    if (!viaPassword && cookie(request, COOKIE) !== token) {
      return new Response("Sign in with the password (any user name).", {
        status: 401,
        headers: {
          "WWW-Authenticate": 'Basic realm="TeaFabric", charset="UTF-8"',
          "Cache-Control": "no-store",
        },
      });
    }
    let response;
    if (url.pathname === "/relay") {
      response = relay(request, url);
    } else if (/^\/download\/[^/]+\.zip$/.test(url.pathname)) {
      response = await serveSplit(request, env, url);
    } else {
      response = await env.ASSETS.fetch(request);
    }
    if (viaPassword && response.status !== 101) {
      response = new Response(response.body, response);
      response.headers.append(
        "Set-Cookie",
        `${COOKIE}=${token}; Path=/; Secure; HttpOnly; SameSite=Strict; Max-Age=2592000`,
      );
    }
    return response;
  },
};

async function hmac(key, data) {
  const k = await crypto.subtle.importKey("raw", encoder.encode(key), { name: "HMAC", hash: "SHA-256" }, false, [
    "sign",
  ]);
  const sig = new Uint8Array(await crypto.subtle.sign("HMAC", k, encoder.encode(data)));
  return [...sig].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function authToken(password) {
  return hmac(password, "teafabric-auth-v1");
}

async function basicAuthOk(request, password) {
  const header = request.headers.get("Authorization") || "";
  if (!header.startsWith("Basic ")) return false;
  let decoded;
  try {
    decoded = atob(header.slice(6));
  } catch {
    return false;
  }
  const given = decoded.slice(decoded.indexOf(":") + 1);
  // Compare digests: equal length, so timingSafeEqual applies.
  const a = encoder.encode(await hmac("compare", given));
  const b = encoder.encode(await hmac("compare", password));
  return crypto.subtle.timingSafeEqual(a, b);
}

function cookie(request, name) {
  for (const part of (request.headers.get("Cookie") || "").split(";")) {
    const [k, ...v] = part.trim().split("=");
    if (k === name) return v.join("=");
  }
  return null;
}

function relay(request, url) {
  if ((request.headers.get("Upgrade") || "").toLowerCase() !== "websocket") {
    return new Response("Expected a WebSocket connection", { status: 426 });
  }
  const target = url.searchParams.get("target") || "";
  const m = /^(\[[0-9a-fA-F:]+\]|[A-Za-z0-9.-]+)(?::(\d{1,5}))?$/.exec(target);
  if (!m) {
    return new Response("Use /relay?target=host:port", { status: 400 });
  }
  const hostname = m[1].replace(/^\[|\]$/g, "");
  const port = m[2] ? Number(m[2]) : 25565;

  const [client, server] = Object.values(new WebSocketPair());
  server.accept();
  let socket;
  try {
    socket = connect({ hostname, port });
  } catch (e) {
    server.close(1011, "Cannot connect to " + target);
    return new Response(null, { status: 101, webSocket: client });
  }
  const writer = socket.writable.getWriter();
  let closed = false;
  const close = (code, reason) => {
    if (closed) return;
    closed = true;
    try {
      server.close(code, reason);
    } catch {}
    try {
      socket.close();
    } catch {}
  };
  server.addEventListener("message", (event) => {
    const data = typeof event.data === "string" ? encoder.encode(event.data) : new Uint8Array(event.data);
    writer.write(data).catch(() => close(1011, "Server connection lost"));
  });
  server.addEventListener("close", () => close(1000, "Closed"));
  server.addEventListener("error", () => close(1011, "WebSocket error"));
  (async () => {
    const reader = socket.readable.getReader();
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        server.send(value);
      }
      close(1000, "Server closed the connection");
    } catch (e) {
      close(1011, "Cannot reach " + target);
    }
  })();
  return new Response(null, { status: 101, webSocket: client });
}

// One file stored as parts: streams the parts in order, or the requested byte range of them, so
// downloads can be resumed. Whole parts are piped without passing through JavaScript; only the parts
// at the ends of a range are read and cut.
async function serveSplit(request, env, url) {
  const manifestResponse = await env.ASSETS.fetch(new Request(url.origin + "/download/parts/manifest.json"));
  if (!manifestResponse.ok) return new Response("Not found", { status: 404 });
  const manifest = await manifestResponse.json();
  if (url.pathname !== "/download/" + manifest.name) return new Response("Not found", { status: 404 });
  const total = manifest.size;
  let start = 0;
  let end = total - 1;
  const headers = new Headers({
    "Content-Type": "application/zip",
    "Content-Disposition": `attachment; filename="${manifest.name}"`,
    "Accept-Ranges": "bytes",
    "Cache-Control": "private, no-transform",
    ETag: `"${manifest.sha256}"`,
  });
  const range = request.headers.get("Range");
  const ifRange = request.headers.get("If-Range");
  let status = 200;
  if (range && (!ifRange || ifRange === headers.get("ETag"))) {
    const m = /^bytes=(\d*)-(\d*)$/.exec(range.trim());
    if (m && (m[1] !== "" || m[2] !== "")) {
      if (m[1] === "") {
        start = Math.max(0, total - Number(m[2]));
      } else {
        start = Number(m[1]);
        if (m[2] !== "") end = Math.min(Number(m[2]), total - 1);
      }
      if (start > end || start >= total) {
        return new Response(null, { status: 416, headers: { "Content-Range": `bytes */${total}` } });
      }
      status = 206;
      headers.set("Content-Range", `bytes ${start}-${end}/${total}`);
    }
  }
  if (request.method === "HEAD") {
    headers.set("Content-Length", String(end - start + 1));
    return new Response(null, { status, headers });
  }
  const { readable, writable } = new FixedLengthStream(end - start + 1);
  (async () => {
    let offset = 0;
    try {
      for (const part of manifest.parts) {
        const partStart = offset;
        const partEnd = offset + part.size - 1;
        offset += part.size;
        if (partEnd < start || partStart > end) continue;
        const from = Math.max(start, partStart) - partStart;
        const to = Math.min(end, partEnd) - partStart;
        // Static assets ignore Range: a part needed only in part is read whole and cut here.
        const partResponse = await env.ASSETS.fetch(new Request(url.origin + "/download/parts/" + part.file));
        if (partResponse.status !== 200) throw new Error(`part ${part.file}: HTTP ${partResponse.status}`);
        if (from === 0 && to === part.size - 1) {
          await partResponse.body.pipeTo(writable, { preventClose: true });
        } else {
          await pipeSlice(partResponse.body, writable, from, to);
        }
      }
      await writable.close();
    } catch (e) {
      await writable.abort(e);
    }
  })();
  return new Response(readable, { status, headers });
}

// Writes bytes from..to (inclusive) of body to writable, leaving writable open.
async function pipeSlice(body, writable, from, to) {
  const reader = body.getReader();
  const writer = writable.getWriter();
  let pos = 0;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      const chunkStart = pos;
      pos += value.byteLength;
      if (pos <= from) continue;
      const a = Math.max(0, from - chunkStart);
      const b = Math.min(value.byteLength, to + 1 - chunkStart);
      if (b > a) await writer.write(value.subarray(a, b));
      if (pos > to) {
        await reader.cancel();
        break;
      }
    }
  } finally {
    writer.releaseLock();
  }
}
