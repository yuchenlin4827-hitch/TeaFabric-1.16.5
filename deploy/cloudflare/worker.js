// TeaFabric on Cloudflare Workers: serves build/web (static assets) to signed-in visitors only, and relays
// WebSocket connections to Minecraft servers (TCP) for multiplayer.
//
// The page contains Minecraft's code and assets, which must not be published: every request needs the
// password (HTTP Basic auth, any user name; the AUTH_PASSWORD secret). A successful sign-in also sets a
// signed cookie, which WebSocket connections to /relay carry.
//
// Multiplayer: in the game, connect to  wss://<this host>/relay?target=mc.example.com:25565
//
// /download/ is public: the HMCL launcher (open source) and a page on joining the server with it.
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
    if (url.pathname === "/download") {
      return Response.redirect(url.origin + "/download/", 301);
    }
    if (url.pathname.startsWith("/download/")) {
      // Plain file names only (no "/", "%" or leading "."), so nothing outside /download/ is reachable.
      const name = url.pathname.slice("/download/".length);
      if (!/^(?:[A-Za-z0-9_-][A-Za-z0-9._-]*)?$/.test(name)) {
        return new Response("Not found", { status: 404 });
      }
      return env.ASSETS.fetch(new Request(url.origin + "/download/" + name, request));
    }
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
