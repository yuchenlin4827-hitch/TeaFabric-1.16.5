// Web Worker (world-worker.js) that runs singleplayer worlds (perf branch): the same classes.wasm as the page, started with
// main(["--worker"]) (teafabric.web.WebWorkerMain). The page hands over the downloaded Wasm and game data, so
// nothing is fetched twice; the worker reads and writes the worlds in the same browser storage (IndexedDB) as the
// page, saves/ only. The page's client connects to the world through boot.js' socket registry
// (ws://teafabric.worker/): each socket here is one end of such a connection.
'use strict';
importScripts('teavm/classes.wasm-runtime.js');

const tf = self.teafabric = {
  archives: {},
  config: {},
  events: [],
  dropNames: [],
  dropData: [],
  clipboard: ''
};

// Java's log lines go to the page's console (prefixed), where tests and players see them.
self.teafabricLog = function (severity, text) {
  self.postMessage({ t: 'log', severity: severity, text: text });
};
const quiet = function () {};
self.console = Object.assign({}, self.console, { info: quiet, debug: quiet, log: quiet, warn: quiet, error: quiet });

// ------------------------------------------------------------------ browser storage (as in boot.js)

tf.store = {
  available: false,
  restored: [],
  pending: new Map(),
  db: null,
  init: async function (persist) {
    if (!persist || !self.indexedDB) return;
    try {
      const db = await new Promise(function (resolve, reject) {
        const req = indexedDB.open('teafabric', 1);
        req.onupgradeneeded = function () { req.result.createObjectStore('files'); };
        req.onsuccess = function () { resolve(req.result); };
        req.onerror = function () { reject(req.error); };
      });
      this.restored = await new Promise(function (resolve, reject) {
        const out = [];
        const cursor = db.transaction('files', 'readonly').objectStore('files').openCursor();
        cursor.onsuccess = function () {
          const c = cursor.result;
          if (!c) { resolve(out); return; }
          out.push([c.key, c.value instanceof Int8Array ? c.value : new Int8Array(c.value)]);
          c.continue();
        };
        cursor.onerror = function () { reject(cursor.error); };
      });
      this.db = db;
      this.available = true;
    } catch (e) {
      self.teafabricLog(2, '[Storage] Browser storage unavailable in the world worker: ' + e);
    }
  },
  put: function (path, data) { this.pending.set(path, data); },
  remove: function (path) { this.pending.set(path, null); },
  flush: function () {
    if (!this.db || this.pending.size === 0) return;
    const batch = this.pending;
    this.pending = new Map();
    const tx = this.db.transaction('files', 'readwrite');
    const files = tx.objectStore('files');
    batch.forEach(function (data, path) {
      if (data === null) files.delete(path); else files.put(data, path);
    });
    tx.onerror = function () { self.teafabricLog(2, '[Storage] Saving to browser storage failed: ' + tx.error); };
  }
};

// ------------------------------------------------------------------ connections from the page

const sockets = {};
const accepted = [];
tf.ws = {
  open: function () { return 0; }, // the world does not connect out
  accept: function () { return accepted.length ? accepted.shift() : 0; },
  state: function (id) { const s = sockets[id]; return s ? s.state : 3; },
  error: function (id) { const s = sockets[id]; return s ? s.error : ''; },
  pending: function (id) { const s = sockets[id]; return s ? s.queue.length : 0; },
  take: function (id) { return sockets[id].queue.shift(); },
  send: function (id, data) {
    const s = sockets[id];
    if (!s || s.state !== 1) return;
    const copy = data.byteOffset === 0 && data.byteLength === data.buffer.byteLength ? data : data.slice();
    self.postMessage({ t: 'data', id: id, bytes: copy.buffer }, [copy.buffer]);
  },
  close: function (id) {
    const s = sockets[id];
    if (!s) return;
    delete sockets[id];
    self.postMessage({ t: 'close', id: id });
  }
};

// ------------------------------------------------------------------ commands (teafabric.game.WorkerBridge)

const commands = [];
tf.host = {
  files: [],
  poll: function () {
    const c = commands.shift();
    if (!c) return null;
    this.files = c.files || [];
    return c.json;
  },
  post: function (json) { self.postMessage({ t: 'status', json: json }); },
  postFile: function (path, data) {
    const copy = data.slice();
    self.postMessage({ t: 'file', path: path, bytes: copy.buffer }, [copy.buffer]);
  }
};
tf.exit = function () {};
tf.fetchAsset = function () { return 0; };
tf.takeAsset = function () { return null; };

// ------------------------------------------------------------------ start

self.onmessage = async function (e) {
  const m = e.data;
  switch (m.t) {
    case 'cmd':
      commands.push(m);
      break;
    case 'open':
      sockets[m.id] = { state: 1, queue: [], error: '' };
      accepted.push(m.id);
      break;
    case 'data': {
      const s = sockets[m.id];
      if (s) s.queue.push(new Int8Array(m.bytes));
      break;
    }
    case 'close': {
      const s = sockets[m.id];
      if (s) s.state = 3;
      break;
    }
    case 'init':
      try {
        Error.stackTraceLimit = 64;
        Object.assign(tf.config, m.config || {});
        tf.archives['/classpath'] = new Int8Array(m.classpath);
        await tf.store.init(m.persist);
        const teavm = await TeaVM.wasmGC.load(new Uint8Array(m.wasm), {
          stackDeobfuscator: { enabled: false },
          installImports: function (imports) {
            imports.teafabricFrame = { request: function () {} }; // no frames in the worker
            imports.teafabricDebug = { enabled: function () { return false; }, block: quiet, unblock: quiet, suspend: quiet };
          }
        });
        teavm.exports.main(['--worker']);
      } catch (err) {
        self.postMessage({ t: 'status', json: JSON.stringify({ t: 'error', text: 'The world worker did not start: ' + (err && err.message || err) }) });
      }
      break;
    default:
      break;
  }
};
