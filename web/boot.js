// TeaFabric boot loader: page-side bridge used by the Java code (teafabric.web.WebWindow,
// org.lwjgl.glfw.GLFW, org.lwjgl.openal.WebAudio), archive download and Wasm start-up.
(function () {
  'use strict';
  const canvas = document.getElementById('game');
  const statusEl = document.getElementById('status');
  const fillEl = document.getElementById('fill');
  const overlay = document.getElementById('overlay');
  const logEl = document.getElementById('log');
  const params = new URLSearchParams(location.search);

  const tf = window.teafabric = {
    canvas: canvas,
    events: [],
    dropNames: [],
    dropData: [],
    archives: {},
    clipboard: '',
    config: {
      username: null, // chosen before the game starts: see chooseName()
      probe: params.has('probe') ? '1' : null,
      threads: params.has('threads') ? '1' : null,
      trace: params.has('trace') ? '1' : null,
      vanillaMain: params.has('vanillaMain') ? '1' : null,
      waits: params.has('waits') ? '1' : null,
      debug: params.has('debug') ? '1' : null,
      netdebug: params.has('netdebug') ? '1' : null,
      server: params.get('server'),
      proxy: params.get('proxy'),
      protocol: params.get('protocol'),
      spawnRadius: params.get('spawnRadius')
    }
  };

  function setStatus(text, progress) {
    statusEl.textContent = text;
    if (progress !== undefined) fillEl.style.width = Math.round(progress * 100) + '%';
  }

  function fail(text) {
    statusEl.textContent = text;
    statusEl.classList.add('error');
    overlay.classList.remove('hidden');
  }

  // Errors from Java logging (LogSink) are mirrored to an on-page console.
  tf.teafabricLog = null;
  window.teafabricLog = function (severity, text) {
    if (params.has('beacon')) {
      // Debugging aid: synchronous request per line, recorded in the server log even if the tab dies.
      try {
        const x = new XMLHttpRequest();
        x.open('GET', '/_log?' + encodeURIComponent(text.split('\n')[0].slice(0, 300)), false);
        x.send();
      } catch (e) { /* ignore */ }
    }
    // The overlay opens by itself only for crashes; everything else is in the console (or ?log).
    const fatal = /^\[FATAL\]|---- Minecraft Crash Report ----|Unhandled exception on the main thread/.test(text);
    if (fatal || params.has('log')) {
      logEl.hidden = false;
    }
    if (severity >= 3 || params.has('log')) {
      logEl.textContent += text + '\n';
      logEl.scrollTop = logEl.scrollHeight;
    }
  };

  // ------------------------------------------------------------------ WebGL / canvas

  function stubGL() { // debugging aid (?nogl): a WebGL 2 context that does nothing
    return new Proxy({}, { get: function (t, name) {
      if (name === 'getParameter') return function (p) { return p === 3379 ? 8192 : 0; };
      if (name === 'getShaderParameter' || name === 'getProgramParameter') return function () { return true; };
      if (name === 'getShaderInfoLog' || name === 'getProgramInfoLog') return function () { return ''; };
      if (name === 'checkFramebufferStatus') return function () { return 36053; };
      if (name === 'getAttribLocation') return function () { return 0; };
      if (String(name).indexOf('create') === 0 || name === 'getUniformLocation') return function () { return {}; };
      return function () { return undefined; };
    } });
  }

  tf.createGL = function () {
    if (params.has('nogl')) { overlay.classList.add('hidden'); return stubGL(); }
    const gl = canvas.getContext('webgl2', {
      alpha: false, depth: true, stencil: true, antialias: false,
      premultipliedAlpha: false, preserveDrawingBuffer: false, powerPreference: 'high-performance'
    });
    if (!gl) throw new Error('WebGL 2 is not available');
    overlay.classList.add('hidden');
    return gl;
  };

  function resize() {
    const dpr = window.devicePixelRatio || 1;
    const w = Math.max(1, Math.round(canvas.clientWidth * dpr));
    const h = Math.max(1, Math.round(canvas.clientHeight * dpr));
    if (canvas.width !== w || canvas.height !== h) {
      canvas.width = w;
      canvas.height = h;
      tf.events.push([6, w, h, 0, 0]);
    }
  }
  new ResizeObserver(resize).observe(canvas);
  resize();

  // ------------------------------------------------------------------ keyboard

  const KEYS = {
    Space: 32, Quote: 39, Comma: 44, Minus: 45, Period: 46, Slash: 47, Semicolon: 59, Equal: 61,
    BracketLeft: 91, Backslash: 92, BracketRight: 93, Backquote: 96, IntlBackslash: 162,
    Escape: 256, Enter: 257, Tab: 258, Backspace: 259, Insert: 260, Delete: 261, ArrowRight: 262,
    ArrowLeft: 263, ArrowDown: 264, ArrowUp: 265, PageUp: 266, PageDown: 267, Home: 268, End: 269,
    CapsLock: 280, ScrollLock: 281, NumLock: 282, PrintScreen: 283, Pause: 284,
    NumpadDecimal: 330, NumpadDivide: 331, NumpadMultiply: 332, NumpadSubtract: 333, NumpadAdd: 334,
    NumpadEnter: 335, NumpadEqual: 336, ShiftLeft: 340, ControlLeft: 341, AltLeft: 342, MetaLeft: 343,
    ShiftRight: 344, ControlRight: 345, AltRight: 346, MetaRight: 347, ContextMenu: 348
  };
  for (let i = 0; i < 26; i++) KEYS['Key' + String.fromCharCode(65 + i)] = 65 + i;
  for (let i = 0; i < 10; i++) { KEYS['Digit' + i] = 48 + i; KEYS['Numpad' + i] = 320 + i; }
  for (let i = 1; i <= 25; i++) KEYS['F' + i] = 289 + i;

  // GLFW modifier bits. Lock keys are left out (GLFW only reports them with GLFW_LOCK_KEY_MODS).
  function mods(e) {
    return (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
  }
  // macOS: Cmd acts as Minecraft's Control (select all / copy / cut / paste shortcuts).
  if (/Mac|iPhone|iPad/.test(navigator.platform || '')) { KEYS.MetaLeft = 341; KEYS.MetaRight = 345; }

  // Text entry goes through a hidden text area instead of being guessed from key events, so that
  // input methods (Chinese, Japanese, ...), dead keys, AltGr layouts and on-screen keyboards work:
  // the browser composes the text, Minecraft receives it as GLFW character events.
  const textInput = document.createElement('textarea');
  textInput.id = 'text-input';
  textInput.setAttribute('autocomplete', 'off');
  textInput.setAttribute('autocorrect', 'off');
  textInput.setAttribute('autocapitalize', 'off');
  textInput.setAttribute('spellcheck', 'false');
  textInput.setAttribute('aria-hidden', 'true');
  textInput.tabIndex = -1;
  textInput.style.cssText = 'position:fixed;left:0;top:0;width:1px;height:1px;padding:0;border:0;margin:0;'
    + 'opacity:0;resize:none;overflow:hidden;font-size:16px;pointer-events:none;z-index:-1;';
  document.body.appendChild(textInput);
  function focusText() {
    if (document.activeElement !== textInput) {
      try { textInput.focus({ preventScroll: true }); } catch (e) { textInput.focus(); }
    }
  }
  function pushText(text) {
    for (const ch of text) { // iterates code points
      const cp = ch.codePointAt(0);
      if (cp >= 32 && cp !== 127) tf.events.push([2, cp, 0, 0, 0]);
    }
  }
  let composing = false;
  function flushText() {
    const text = textInput.value;
    if (text) { textInput.value = ''; pushText(text); }
  }
  textInput.addEventListener('compositionstart', function () { composing = true; });
  textInput.addEventListener('compositionend', function () { composing = false; flushText(); });
  textInput.addEventListener('input', function (e) { if (!composing && !e.isComposing) flushText(); });

  const pressed = new Set();
  function pushKey(key, action, m) {
    if (action === 0) pressed.delete(key); else pressed.add(key);
    tf.events.push([1, key, key, action, m]);
  }
  // Paste: the browser's own paste event carries the text (no clipboard permission needed); Minecraft
  // then sees Ctrl+V and reads the clipboard string.
  textInput.addEventListener('paste', function (e) {
    const text = e.clipboardData && e.clipboardData.getData('text');
    e.preventDefault();
    if (typeof text === 'string') tf.clipboard = text;
    const hadCtrl = pressed.has(341) || pressed.has(345);
    if (!hadCtrl) pushKey(341, 1, 2);
    pushKey(86, 1, 2);
    pushKey(86, 0, 2);
    if (!hadCtrl) pushKey(341, 0, 0);
  });

  const passThrough = new Set(['F5', 'F11', 'F12']);
  window.addEventListener('keydown', function (e) {
    if (e.isComposing || e.keyCode === 229) return; // the input method is handling this key
    const key = KEYS[e.code] !== undefined ? KEYS[e.code] : -1;
    const shortcut = (e.ctrlKey || e.metaKey) && !e.altKey; // AltGr reports Ctrl+Alt and still types text
    if (shortcut && e.code === 'KeyV') return; // handled by the paste event
    pushKey(key, e.repeat ? 2 : 1, mods(e));
    const types = e.key && (e.key.length === 1 || e.key === 'Dead' || e.key === 'Unidentified');
    if (types && !shortcut) {
      if (document.activeElement !== textInput) {
        // Not focused yet (first key after load): this key's text would be lost, deliver it directly.
        focusText();
        if (e.key.length === 1) pushText(e.key);
        e.preventDefault();
      }
      return; // let the browser type into the hidden text area
    }
    if (!passThrough.has(e.code)) e.preventDefault();
  });
  window.addEventListener('keyup', function (e) {
    if (e.isComposing || e.keyCode === 229) return;
    const key = KEYS[e.code] !== undefined ? KEYS[e.code] : -1;
    if (key === -1 || pressed.has(key)) pushKey(key, 0, mods(e));
    if (!passThrough.has(e.code)) e.preventDefault();
  });
  // Keys held while the page loses focus never get a keyup: release them.
  window.addEventListener('blur', function () {
    pressed.forEach(function (key) { tf.events.push([1, key, key, 0, 0]); });
    pressed.clear();
  });
  window.addEventListener('focus', focusText);
  focusText();
  tf.setClipboard = function (text) {
    tf.clipboard = text;
    if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(text).catch(function () {});
  };

  // ------------------------------------------------------------------ mouse

  let vx = 0, vy = 0;
  function locked() { return document.pointerLockElement === canvas; }
  canvas.addEventListener('mousemove', function (e) {
    const dpr = window.devicePixelRatio || 1;
    if (locked()) { vx += e.movementX * dpr; vy += e.movementY * dpr; }
    else { vx = e.offsetX * dpr; vy = e.offsetY * dpr; }
    tf.events.push([3, vx, vy, 0, 0]);
  });
  const BUTTONS = [0, 2, 1, 3, 4]; // DOM -> GLFW (left, middle, right)
  canvas.addEventListener('mousedown', function (e) {
    focusText();
    if (tf.audio && tf.audio.state === 'suspended') tf.audio.resume();
    if (tf.wantLock && !locked()) canvas.requestPointerLock();
    tf.events.push([4, BUTTONS[e.button] || e.button, 1, mods(e), 0]);
    e.preventDefault();
  });
  window.addEventListener('mouseup', function (e) {
    tf.events.push([4, BUTTONS[e.button] || e.button, 0, mods(e), 0]);
  });
  canvas.addEventListener('contextmenu', function (e) { e.preventDefault(); });
  canvas.addEventListener('wheel', function (e) {
    const dy = e.deltaY === 0 ? 0 : (e.deltaY > 0 ? -1 : 1);
    const dx = e.deltaX === 0 ? 0 : (e.deltaX > 0 ? -1 : 1);
    tf.events.push([5, dx, dy, 0, 0]);
    e.preventDefault();
  }, { passive: false });
  canvas.addEventListener('mouseenter', function () { tf.events.push([8, 1, 0, 0, 0]); });
  canvas.addEventListener('mouseleave', function () { tf.events.push([8, 0, 0, 0, 0]); });
  window.addEventListener('focus', function () { tf.events.push([7, 1, 0, 0, 0]); });
  window.addEventListener('blur', function () { tf.events.push([7, 0, 0, 0, 0]); });

  tf.wantLock = false;
  tf.setPointerLock = function (lock) {
    tf.wantLock = lock;
    if (lock && !locked()) {
      try { const p = canvas.requestPointerLock(); if (p && p.catch) p.catch(function () {}); } catch (e) {}
    } else if (!lock && locked()) {
      document.exitPointerLock();
    }
  };
  document.addEventListener('pointerlockchange', function () {
    // The user pressed Esc (the browser releases the lock): tell Minecraft so it opens the pause menu.
    if (!locked() && tf.wantLock) {
      tf.events.push([1, 256, 256, 1, 0]);
      tf.events.push([1, 256, 256, 0, 0]);
    }
  });

  // ------------------------------------------------------------------ WebSockets (multiplayer)

  // teafabric.web.net.WebSocketChannel: binary frames carry the raw Minecraft protocol stream.
  const sockets = {};
  let nextSocket = 1;
  tf.ws = {
    open: function (url) {
      const id = nextSocket++;
      const s = sockets[id] = { state: 0, queue: [], error: '', ws: null };
      try {
        const ws = s.ws = new WebSocket(url);
        ws.binaryType = 'arraybuffer';
        ws.onopen = function () { s.state = 1; };
        ws.onmessage = function (e) { if (typeof e.data !== 'string') s.queue.push(new Int8Array(e.data)); };
        ws.onerror = function () { if (!s.error) s.error = s.state === 0 ? 'connection failed' : 'connection error'; };
        ws.onclose = function (e) {
          if (!s.error && e.code !== 1000 && e.code !== 1005) s.error = 'closed, code ' + e.code + (e.reason ? ': ' + e.reason : '');
          s.state = 3;
        };
      } catch (e) {
        s.state = 3;
        s.error = String((e && e.message) || e);
      }
      return id;
    },
    state: function (id) { const s = sockets[id]; return s ? s.state : 3; },
    error: function (id) { const s = sockets[id]; return s ? s.error : ''; },
    pending: function (id) { const s = sockets[id]; return s ? s.queue.length : 0; },
    take: function (id) { return sockets[id].queue.shift(); },
    send: function (id, data) { const s = sockets[id]; if (s && s.ws && s.ws.readyState === 1) s.ws.send(data); },
    close: function (id) {
      const s = sockets[id];
      if (!s) return;
      delete sockets[id];
      try { if (s.ws && s.ws.readyState < 2) s.ws.close(1000); } catch (e) { /* ignore */ }
    }
  };

  // ------------------------------------------------------------------ sounds

  // Ogg Vorbis decoding for teafabric.web.audio.WebSound. Vanilla sounds are fetched from
  // sounds/<hash>.ogg when first played; resource-pack sounds arrive as bytes. The decoded AudioBuffer
  // stays here until Java releases it; Java reads it as 16-bit PCM through copy().
  const decoded = new Map();
  let decodeContext = null;
  function decoderContext() {
    if (tf.audio) return tf.audio; // decode at the playback rate when the sound engine is up
    if (!decodeContext) {
      const Offline = window.OfflineAudioContext || window.webkitOfflineAudioContext;
      decodeContext = new Offline(2, 1, 48000);
    }
    return decodeContext;
  }
  tf.sound = {
    stats: { loaded: 0, failed: 0 },
    load: function (id, hash, data) {
      let ctx;
      try {
        ctx = decoderContext();
      } catch (e) {
        return false; // no WebAudio (?noaudio)
      }
      const bytes = hash
        ? fetch('sounds/' + hash + '.ogg').then(function (r) {
            if (!r.ok) throw new Error('HTTP ' + r.status);
            return r.arrayBuffer();
          })
        : Promise.resolve(data.buffer.slice(data.byteOffset, data.byteOffset + data.byteLength));
      bytes.then(function (buf) {
        return new Promise(function (resolve, reject) { ctx.decodeAudioData(buf, resolve, reject); });
      }).then(function (audio) {
        decoded.set(id, audio);
        tf.sound.stats.loaded++;
        wasmExports.teafabricSoundLoaded(id, 1);
      }, function (e) {
        console.warn('[sound] ' + (hash ? 'sounds/' + hash + '.ogg' : 'Ogg data') + ': ' + (e && e.message ? e.message : e));
        tf.sound.stats.failed++;
        wasmExports.teafabricSoundLoaded(id, 0);
      });
      return true;
    },
    get: function (id) {
      return decoded.get(id);
    },
    copy: function (id, start, count, mem, ptr) {
      const audio = decoded.get(id);
      const channels = audio.numberOfChannels;
      const out = new Int16Array(mem, ptr, count * channels);
      for (let c = 0; c < channels; c++) {
        const src = audio.getChannelData(c);
        for (let i = 0, j = c; i < count; i++, j += channels) {
          const v = src[start + i];
          out[j] = v >= 1 ? 32767 : v <= -1 ? -32768 : (v * 32767) | 0;
        }
      }
    },
    release: function (id) {
      decoded.delete(id);
    }
  };

  // Other assets fetched on demand (teafabric.web.vfs.WebAssets), e.g. lang/<hash>.json.
  const fetchedAssets = new Map();
  tf.fetchAsset = function (id, url) {
    fetch(url).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.arrayBuffer();
    }).then(function (buf) {
      fetchedAssets.set(id, new Int8Array(buf));
      wasmExports.teafabricAssetFetched(id, 1);
    }, function (e) {
      console.warn('[assets] ' + url + ': ' + (e && e.message ? e.message : e));
      wasmExports.teafabricAssetFetched(id, 0);
    });
    return true;
  };
  tf.takeAsset = function (id) {
    const data = fetchedAssets.get(id);
    fetchedAssets.delete(id);
    return data || null;
  };

  // ------------------------------------------------------------------ files, fullscreen, exit

  canvas.addEventListener('dragover', function (e) { e.preventDefault(); });
  canvas.addEventListener('drop', function (e) {
    e.preventDefault();
    const files = Array.from(e.dataTransfer.files || []);
    Promise.all(files.map(function (f) { return f.arrayBuffer(); })).then(function (buffers) {
      files.forEach(function (f, i) { tf.dropNames.push(f.name); tf.dropData.push(new Int8Array(buffers[i])); });
      tf.events.push([10, files.length, 0, 0, 0]);
    });
  });
  tf.setFullscreen = function (on) {
    if (on && !document.fullscreenElement) document.documentElement.requestFullscreen().catch(function () {});
    else if (!on && document.fullscreenElement) document.exitFullscreen();
  };
  tf.exit = function (status) {
    fail(status === 0 ? 'Minecraft has stopped. Reload the page to play again.'
                      : 'Minecraft stopped unexpectedly (status ' + status + '). See the log below.');
  };

  // ------------------------------------------------------------------ start-up

  const nativeFetch = window.fetch.bind(window); // serveDownloadsToRuntime() overrides window.fetch
  async function fetchWithProgress(url, label, weight, base, shownBefore, shownTotal) {
    const response = await nativeFetch(url);
    if (!response.ok) throw new Error(url + ': HTTP ' + response.status);
    // A compressing host (Cloudflare) sends the compressed length, or none: then progress is approximate.
    const total = +response.headers.get('Content-Length') || 0;
    const before = shownBefore || 0;
    if (!response.body) {
      const buf = await response.arrayBuffer();
      setStatus(label, base + weight);
      return buf;
    }
    const reader = response.body.getReader();
    const chunks = [];
    let received = 0;
    for (;;) {
      const r = await reader.read();
      if (r.done) break;
      chunks.push(r.value);
      received += r.value.length;
      const mib = Math.round((before + received) / 1048576);
      const of = shownTotal || total;
      setStatus(label + ' ' + mib + (of ? ' / ' + Math.round(of / 1048576) : '') + ' MiB',
        base + weight * (total ? Math.min(1, received / total) : 0.5));
    }
    const out = new Uint8Array(received);
    let offset = 0;
    for (const c of chunks) { out.set(c, offset); offset += c.length; }
    setStatus(label, base + weight);
    return out.buffer;
  }

  // Hosted builds (scripts/deploy-cloudflare.sh) list their large files in files.json: each is stored
  // gzip-compressed (classes.wasm: 100 MB -> 21 MB) under a content-hashed name, in parts below the
  // host's per-file limit (Cloudflare: 25 MiB). The browser decompresses natively (DecompressionStream).
  // Without files.json (local builds, the downloadable version) the files are fetched as they are.
  let manifest = null;
  async function loadManifest() {
    try {
      const r = await fetch('files.json', { cache: 'no-cache' });
      if (r.ok) manifest = await r.json();
    } catch (e) {
      // plain files
    }
  }

  async function gunzip(blob) {
    const head = new Uint8Array(await blob.slice(0, 2).arrayBuffer());
    if (head[0] !== 0x1f || head[1] !== 0x8b) return blob.arrayBuffer(); // already decoded on the way
    return new Response(blob.stream().pipeThrough(new DecompressionStream('gzip'))).arrayBuffer();
  }

  // Downloads a file (by its plain path) with progress; returns an ArrayBuffer.
  const downloads = {};
  function download(path, label, weight, base) {
    if (!downloads[path]) downloads[path] = downloadNow(path, label, weight, base);
    return downloads[path];
  }
  async function downloadNow(path, label, weight, base) {
    const entry = manifest && manifest.files && manifest.files[path];
    if (!entry) return fetchWithProgress(path, label, weight, base);
    const total = entry.parts.reduce(function (n, p) { return n + p.size; }, 0) || 1;
    const chunks = [];
    let done = 0;
    for (let i = 0; i < entry.parts.length; i++) {
      const part = entry.parts[i];
      chunks.push(await fetchWithProgress(part.file, label, weight * part.size / total, base + weight * done / total,
        done, total));
      done += part.size;
    }
    const blob = new Blob(chunks);
    return entry.gzip ? gunzip(blob) : blob.arrayBuffer();
  }

  // The TeaVM runtime fetches teavm/classes.wasm and its debug info itself: serve them from download().
  function serveDownloadsToRuntime() {
    // Without files.json only the Wasm module is downloaded here (for the progress bar).
    const paths = manifest && manifest.files ? Object.keys(manifest.files) : ['teavm/classes.wasm'];
    window.fetch = function (input, init) {
      const url = typeof input === 'string' ? input : (input && input.url) || '';
      for (const path of paths) {
        if (url === path || url.endsWith('/' + path)) {
          return download(path, 'Downloading ' + path, 0, 0).then(function (buf) {
            return new Response(buf, { headers: { 'Content-Type': path.endsWith('.wasm') ? 'application/wasm'
              : 'application/octet-stream' } });
          });
        }
      }
      return nativeFetch(input, init);
    };
  }

  // Optionally compile without the native "js-string" builtins (TeaVM then uses JS fallbacks).
  function disableStringBuiltins() {
    const strip = function (opts) {
      if (!opts || !opts.builtins) return opts;
      const copy = Object.assign({}, opts);
      delete copy.builtins;
      return copy;
    };
    const compile = WebAssembly.compile, compileStreaming = WebAssembly.compileStreaming;
    const validate = WebAssembly.validate;
    WebAssembly.compile = function (src, opts) { return compile.call(WebAssembly, src, strip(opts)); };
    WebAssembly.compileStreaming = function (src, opts) { return compileStreaming.call(WebAssembly, src, strip(opts)); };
    WebAssembly.validate = function (src, opts) { return validate.call(WebAssembly, src, strip(opts)); };
  }
  // Java stack traces are taken from Error.stack; V8 keeps only 10 frames by default.
  Error.stackTraceLimit = 64;
  if (params.has('nobuiltins')) disableStringBuiltins();
  if (params.has('noaudio')) { window.AudioContext = undefined; window.webkitAudioContext = undefined; }
  // Engine A/B switches: ?nofinalize stops GC callbacks re-entering Wasm, ?noweak makes weak references strong.
  if (params.has('nofinalize')) {
    window.FinalizationRegistry = function () { this.register = function () {}; this.unregister = function () {}; };
  }
  if (params.has('noweak')) {
    window.WeakRef = function (v) { this.deref = function () { return v; }; };
  }

  // Browser storage for the game directory: worlds, options, server list, config, resource packs.
  // Read completely before the game starts; WebPersistence.java hands over changed files every few seconds.
  tf.store = {
    available: false,
    restored: [],
    pending: new Map(),
    db: null,
    init: async function () {
      if (params.has('nopersist') || !window.indexedDB) return;
      try {
        if (params.has('resetstorage')) {
          await new Promise(function (resolve) {
            const del = indexedDB.deleteDatabase('teafabric');
            del.onsuccess = del.onerror = del.onblocked = function () { resolve(); };
          });
        }
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
        if (navigator.storage && navigator.storage.persist) navigator.storage.persist().catch(function () {});
      } catch (e) {
        console.warn('[Storage] Browser storage (IndexedDB) unavailable; worlds will not be kept: ' + e);
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
      tx.onerror = function () { console.warn('[Storage] Saving to browser storage failed: ' + tx.error); };
    }
  };

  // DesktopCompat.java: "Open ... Folder" buttons download the folder as a zip.
  tf.download = function (name, data) {
    const url = URL.createObjectURL(new Blob([data], { type: 'application/octet-stream' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = name;
    document.body.appendChild(a);
    a.click();
    setTimeout(function () { URL.revokeObjectURL(url); a.remove(); }, 10000);
  };

  // ------------------------------------------------------------------ player name

  // Minecraft names: 3-16 letters, digits or _. "Player", the old default, is refused: many people would
  // share it, and servers (the Railway one included) ban it.
  function validName(name) {
    return typeof name === 'string' && /^[A-Za-z0-9_]{3,16}$/.test(name) && name.toLowerCase() !== 'player';
  }

  function storedName() {
    try {
      return localStorage.getItem('teafabric.username');
    } catch (e) {
      return null;
    }
  }

  // ?username=Name uses that name; otherwise the name saved by an earlier visit. Without one (or with
  // ?rename) the loading panel asks, while the game downloads.
  function chooseName() {
    const given = params.get('username');
    if (validName(given)) return Promise.resolve(given);
    const saved = storedName();
    if (!params.has('rename') && !given && validName(saved)) return Promise.resolve(saved);
    const form = document.getElementById('name-form');
    const input = document.getElementById('name-input');
    const hint = document.getElementById('name-hint');
    input.value = validName(given) ? given : validName(saved) ? saved : '';
    form.hidden = false;
    if (given) {
      hint.textContent = '"' + given + '" cannot be used. Choose another name: 3–16 letters, digits or _ (not "Player").';
      hint.classList.add('error');
    }
    input.focus();
    return new Promise(function (resolve) {
      form.addEventListener('submit', function (event) {
        event.preventDefault();
        const name = input.value.trim();
        if (!validName(name)) {
          hint.textContent = name.toLowerCase() === 'player'
            ? '"Player" is not allowed: choose your own name.'
            : 'Use 3–16 letters, digits or _.';
          hint.classList.add('error');
          input.focus();
          return;
        }
        try {
          localStorage.setItem('teafabric.username', name);
        } catch (e) { /* not kept: asked again next time */ }
        form.hidden = true;
        resolve(name);
      });
    });
  }

  let wasmExports = null;

  async function start() {
    if (typeof WebAssembly !== 'object' || !TeaVM || !TeaVM.wasmGC) {
      fail('This browser does not support WebAssembly.');
      return;
    }
    try {
      const nameChosen = chooseName().then(function (name) { tf.config.username = name; });
      const storeReady = tf.store.init();
      await loadManifest();
      serveDownloadsToRuntime();
      tf.archives['/classpath'] = await download('data/classpath.zip', 'Downloading game resources', 0.15, 0);
      tf.archives['/minecraft/assets'] = await download('data/assets.zip', 'Downloading assets', 0.15, 0.15);
      await download('teavm/classes.wasm', 'Downloading game code', 0.2, 0.3);
      await storeReady;
      if (!tf.config.username) {
        setStatus('Enter a player name to start.', 0.5);
        await nameChosen;
      }
      setStatus('Compiling WebAssembly…', 0.55);
      const teavm = await TeaVM.wasmGC.load('teavm/classes.wasm', {
        stackDeobfuscator: { enabled: !params.has('nodeobf'), infoLocation: 'external' },
        installImports: function (imports) {
          // Frame pacing for glfwSwapBuffers (see WebWindow.waitForFrame).
          imports.teafabricFrame = {
            request: function () {
              requestAnimationFrame(function () { wasmExports.teafabricFrameReady(); });
            }
          };
          // ?waits: the patched TObject reports threads blocked on monitors / Object.wait.
          const blocked = globalThis.__tfBlocked = {};
          const kinds = ['', 'monitor enter', 'Object.wait()', 'Object.wait(timeout)'];
          imports.teafabricDebug = {
            enabled: function () { return params.has('waits'); },
            block: function (id, kind) { blocked[id] = { what: kinds[kind], since: Date.now(), stack: new Error().stack }; },
            unblock: function (id) { delete blocked[id]; },
            suspend: function (state) {
              if (!params.has('waits')) return;
              const log = globalThis.__tfSuspends || (globalThis.__tfSuspends = { count: {}, last: [] });
              log.count[state] = (log.count[state] || 0) + 1;
              const entry = { state: state, at: Date.now(), stack: new Error().stack };
              log.last.push(entry);
              if (log.last.length > 12) log.last.shift();
              if (/glfwSwapBuffers|waitForFrame/.test(entry.stack)) (log.swap || (log.swap = [])).push(entry);
              if (!/startWatchdog|waitForFrame/.test(entry.stack)) {
                const other = log.other || (log.other = []);
                other.push(entry);
                if (other.length > 12) other.shift();
              }
            }
          };
        }
      });
      wasmExports = teavm.instance.exports;
      setStatus('Starting Minecraft…', 0.7);
      teavm.exports.main([]);
    } catch (e) {
      console.error(e);
      fail('Failed to start: ' + (e && e.message ? e.message : e)
        + '\n\nWebAssembly GC requires Chrome/Edge 119+, Firefox 120+ or Safari 18.2+.');
    }
  }
  window.addEventListener('beforeunload', function () {
    // Shutdown hooks are best effort; saving happens when the world is left from the menu.
  });
  start();
})();
