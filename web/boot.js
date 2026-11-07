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
      username: params.get('username') || localStorage.getItem('teafabric.username') || 'Player',
      probe: params.has('probe') ? '1' : null,
      threads: params.has('threads') ? '1' : null,
      trace: params.has('trace') ? '1' : null,
      vanillaMain: params.has('vanillaMain') ? '1' : null
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
    if (severity >= 3 || params.has('log')) {
      logEl.hidden = false;
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

  function mods(e) {
    return (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0)
      | (e.getModifierState && e.getModifierState('CapsLock') ? 16 : 0)
      | (e.getModifierState && e.getModifierState('NumLock') ? 32 : 0);
  }

  const passThrough = new Set(['F5', 'F11', 'F12']);
  window.addEventListener('keydown', function (e) {
    const key = KEYS[e.code] !== undefined ? KEYS[e.code] : -1;
    tf.events.push([1, key, key, e.repeat ? 2 : 1, mods(e)]);
    if (e.key && e.key.length === 1 && !e.ctrlKey && !e.metaKey) {
      tf.events.push([2, e.key.codePointAt(0), mods(e), 0, 0]);
    }
    // Ctrl+V: the async clipboard read completes before Minecraft polls the clipboard string.
    if ((e.ctrlKey || e.metaKey) && e.code === 'KeyV' && navigator.clipboard && navigator.clipboard.readText) {
      navigator.clipboard.readText().then(function (t) { tf.clipboard = t; }).catch(function () {});
    }
    if (!passThrough.has(e.code)) e.preventDefault();
  });
  window.addEventListener('keyup', function (e) {
    const key = KEYS[e.code] !== undefined ? KEYS[e.code] : -1;
    tf.events.push([1, key, key, 0, mods(e)]);
    if (!passThrough.has(e.code)) e.preventDefault();
  });
  window.addEventListener('paste', function (e) {
    tf.clipboard = (e.clipboardData && e.clipboardData.getData('text')) || tf.clipboard;
  });
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
    canvas.focus();
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

  async function fetchWithProgress(url, label, weight, base) {
    const response = await fetch(url);
    if (!response.ok) throw new Error(url + ': HTTP ' + response.status);
    const total = +response.headers.get('Content-Length') || 0;
    if (!response.body || !total) {
      const buf = await response.arrayBuffer();
      setStatus(label, base + weight);
      return buf;
    }
    const reader = response.body.getReader();
    const out = new Uint8Array(total);
    let received = 0;
    for (;;) {
      const r = await reader.read();
      if (r.done) break;
      out.set(r.value, received);
      received += r.value.length;
      setStatus(label + ' ' + Math.round(received / 1048576) + ' / ' + Math.round(total / 1048576) + ' MiB',
        base + weight * received / total);
    }
    return out.buffer;
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
  if (params.has('nobuiltins')) disableStringBuiltins();
  if (params.has('noaudio')) { window.AudioContext = undefined; window.webkitAudioContext = undefined; }
  // Engine A/B switches: ?nofinalize stops GC callbacks re-entering Wasm, ?noweak makes weak references strong.
  if (params.has('nofinalize')) {
    window.FinalizationRegistry = function () { this.register = function () {}; this.unregister = function () {}; };
  }
  if (params.has('noweak')) {
    window.WeakRef = function (v) { this.deref = function () { return v; }; };
  }

  async function start() {
    if (typeof WebAssembly !== 'object' || !TeaVM || !TeaVM.wasmGC) {
      fail('This browser does not support WebAssembly.');
      return;
    }
    try {
      tf.archives['/classpath'] = await fetchWithProgress('data/classpath.zip', 'Downloading game resources', 0.35, 0);
      tf.archives['/minecraft/assets'] = await fetchWithProgress('data/assets.zip', 'Downloading assets', 0.15, 0.35);
      setStatus('Compiling WebAssembly…', 0.55);
      const teavm = await TeaVM.wasmGC.load('teavm/classes.wasm', {
        stackDeobfuscator: { enabled: !params.has('nodeobf'), infoLocation: 'external' }
      });
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
