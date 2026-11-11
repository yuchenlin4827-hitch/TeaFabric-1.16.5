#!/usr/bin/env node
// TeaFabric WebSocket-to-TCP proxy: lets the browser client reach a Minecraft server.
// The browser speaks the raw Minecraft protocol inside binary WebSocket frames; this relays the bytes.
// No dependencies (Node.js 16+).
//
//   node ws-proxy.js --target mc.example.com:25565                 ws://<this host>:8081/
//   node ws-proxy.js --target localhost:25565 --cert c.pem --key k.pem --listen 443     wss://...
//   node ws-proxy.js --allow mc1.example.com,mc2.example.com:25566  clients choose with ?target=host:port
//
// Options:
//   --listen <port>          port to listen on (default 8081)
//   --host <address>         address to bind (default 0.0.0.0)
//   --target <host[:port]>   server used when the client does not ask for one
//   --allow <list>           comma separated host or host:port entries clients may ask for (?target=)
//   --allow-any              clients may ask for any server (an open relay: only on trusted networks)
//   --cert <file> --key <file>   serve wss:// (TLS) directly; otherwise put a TLS reverse proxy in front
//   --origin <list>          comma separated page origins allowed to connect (default: any)
'use strict';

const http = require('http');
const https = require('https');
const net = require('net');
const crypto = require('crypto');
const fs = require('fs');

const opts = { listen: 8081, host: '0.0.0.0', target: null, allow: [], allowAny: false, cert: null, key: null, origins: [] };
const argv = process.argv.slice(2);
for (let i = 0; i < argv.length; i++) {
  const a = argv[i];
  const value = () => {
    if (i + 1 >= argv.length) { console.error('Missing value for ' + a); process.exit(2); }
    return argv[++i];
  };
  if (a === '--listen') opts.listen = parseInt(value(), 10);
  else if (a === '--host') opts.host = value();
  else if (a === '--target') opts.target = value();
  else if (a === '--allow') opts.allow = opts.allow.concat(value().split(',').map((s) => s.trim().toLowerCase()).filter(Boolean));
  else if (a === '--allow-any') opts.allowAny = true;
  else if (a === '--cert') opts.cert = value();
  else if (a === '--key') opts.key = value();
  else if (a === '--origin') opts.origins = opts.origins.concat(value().split(',').map((s) => s.trim()).filter(Boolean));
  else if (a === '--help' || a === '-h') {
    console.log(fs.readFileSync(__filename, 'utf8').split('\n').slice(1, 18).map((l) => l.replace(/^\/\/ ?/, '')).join('\n'));
    process.exit(0);
  } else { console.error('Unknown option ' + a + ' (see --help)'); process.exit(2); }
}
if (!opts.target && !opts.allowAny && opts.allow.length === 0) {
  console.error('Nothing to relay to: give --target host:port (or --allow / --allow-any). See --help.');
  process.exit(2);
}

function parseHostPort(text) {
  if (!text) return null;
  const m = /^\s*(\[[0-9a-fA-F:]+\]|[^:\s]+)(?::(\d{1,5}))?\s*$/.exec(text);
  if (!m) return null;
  const port = m[2] ? parseInt(m[2], 10) : 25565;
  if (port < 1 || port > 65535) return null;
  return { host: m[1].replace(/^\[|\]$/g, ''), port: port };
}

function chooseTarget(requested) {
  const fallback = parseHostPort(opts.target);
  if (!requested) return fallback;
  const t = parseHostPort(requested);
  if (!t) return null;
  const name = t.host.toLowerCase();
  if (opts.allowAny) return t;
  if (opts.allow.includes(name) || opts.allow.includes(name + ':' + t.port)) return t;
  if (fallback && fallback.host.toLowerCase() === name && fallback.port === t.port) return t;
  return null;
}

const MAX_FRAME = 16 * 1024 * 1024;

function frame(opcode, payload) {
  const n = payload.length;
  let header;
  if (n < 126) {
    header = Buffer.from([0x80 | opcode, n]);
  } else if (n < 65536) {
    header = Buffer.alloc(4);
    header[0] = 0x80 | opcode; header[1] = 126; header.writeUInt16BE(n, 2);
  } else {
    header = Buffer.alloc(10);
    header[0] = 0x80 | opcode; header[1] = 127; header.writeUInt32BE(0, 2); header.writeUInt32BE(n, 6);
  }
  return Buffer.concat([header, payload]);
}

function onRequest(req, res) {
  res.writeHead(426, { 'Content-Type': 'text/plain; charset=utf-8', Upgrade: 'websocket' });
  res.end('TeaFabric WebSocket proxy. Enter this address (ws:// or wss://) as the server address in the game.\n');
}

function onUpgrade(req, socket, head) {
  const peer = socket.remoteAddress + ':' + socket.remotePort;
  const key = req.headers['sec-websocket-key'];
  const refuse = (status, why) => {
    console.log(`[${peer}] refused: ${why}`);
    socket.end(`HTTP/1.1 ${status}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  };
  if (!key || String(req.headers.upgrade || '').toLowerCase() !== 'websocket') return refuse('400 Bad Request', 'not a WebSocket handshake');
  if (opts.origins.length && !opts.origins.includes(req.headers.origin || '')) return refuse('403 Forbidden', 'origin ' + req.headers.origin);
  let requested = null;
  try { requested = new URL(req.url, 'http://proxy.invalid').searchParams.get('target'); } catch (e) { /* no target */ }
  const target = chooseTarget(requested);
  if (!target) return refuse('403 Forbidden', requested ? 'target not allowed: ' + requested : 'no target configured');

  const accept = crypto.createHash('sha1').update(key + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
  socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n'
    + 'Sec-WebSocket-Accept: ' + accept + '\r\n\r\n');
  socket.setNoDelay(true);
  console.log(`[${peer}] -> ${target.host}:${target.port}`);

  const tcp = net.connect({ host: target.host, port: target.port });
  tcp.setNoDelay(true);
  let done = false;
  let buffered = head && head.length ? Buffer.from(head) : Buffer.alloc(0);
  let fragments = null;

  function finish(code, why) {
    if (done) return;
    done = true;
    console.log(`[${peer}] closed${why ? ': ' + why : ''}`);
    try {
      const body = Buffer.alloc(2);
      body.writeUInt16BE(code, 0);
      socket.end(frame(0x8, body));
    } catch (e) { socket.destroy(); }
    tcp.destroy();
  }

  tcp.on('data', (chunk) => {
    if (done) return;
    if (!socket.write(frame(0x2, chunk))) {
      tcp.pause();
      socket.once('drain', () => tcp.resume());
    }
  });
  tcp.on('error', (e) => finish(1011, 'server: ' + e.message));
  tcp.on('close', () => finish(1000, 'server closed the connection'));
  socket.on('error', (e) => finish(1006, 'client: ' + e.message));
  socket.on('close', () => finish(1000, ''));

  function deliver(payload) {
    if (payload.length && !tcp.write(payload)) {
      socket.pause();
      tcp.once('drain', () => socket.resume());
    }
  }

  function parse() {
    while (!done) {
      if (buffered.length < 2) return;
      const fin = (buffered[0] & 0x80) !== 0;
      const opcode = buffered[0] & 0x0f;
      const masked = (buffered[1] & 0x80) !== 0;
      let length = buffered[1] & 0x7f;
      let offset = 2;
      if (length === 126) {
        if (buffered.length < 4) return;
        length = buffered.readUInt16BE(2); offset = 4;
      } else if (length === 127) {
        if (buffered.length < 10) return;
        if (buffered.readUInt32BE(2) !== 0) return finish(1009, 'frame too large');
        length = buffered.readUInt32BE(6); offset = 10;
      }
      if (length > MAX_FRAME) return finish(1009, 'frame too large');
      if (!masked) return finish(1002, 'unmasked client frame');
      if (buffered.length < offset + 4 + length) return;
      const mask = buffered.subarray(offset, offset + 4);
      const payload = Buffer.from(buffered.subarray(offset + 4, offset + 4 + length));
      for (let i = 0; i < payload.length; i++) payload[i] ^= mask[i & 3];
      buffered = buffered.subarray(offset + 4 + length);

      if (opcode === 0x8) return finish(1000, 'client closed');
      if (opcode === 0x9) { socket.write(frame(0xA, payload)); continue; }
      if (opcode === 0xA) continue;
      if (opcode === 0x2 || opcode === 0x0) {
        if (fin && fragments === null) { deliver(payload); continue; }
        fragments = fragments === null ? [payload] : fragments.concat([payload]);
        if (fin) { deliver(Buffer.concat(fragments)); fragments = null; }
        continue;
      }
      if (opcode === 0x1) { if (!fin) fragments = null; continue; } // text frames are not part of the protocol
      return finish(1002, 'unsupported opcode ' + opcode);
    }
  }

  socket.on('data', (chunk) => {
    buffered = buffered.length ? Buffer.concat([buffered, chunk]) : chunk;
    parse();
  });
  if (buffered.length) parse();
}

const tls = opts.cert && opts.key;
const server = tls
  ? https.createServer({ cert: fs.readFileSync(opts.cert), key: fs.readFileSync(opts.key) }, onRequest)
  : http.createServer(onRequest);
server.on('upgrade', onUpgrade);
server.listen(opts.listen, opts.host, () => {
  const scheme = tls ? 'wss' : 'ws';
  console.log(`TeaFabric proxy listening on ${scheme}://${opts.host}:${opts.listen}/`);
  if (opts.target) console.log('  default server: ' + opts.target);
  if (opts.allowAny) console.log('  clients may ask for ANY server with ?target=host:port (open relay)');
  else if (opts.allow.length) console.log('  clients may ask for: ' + opts.allow.join(', '));
});
