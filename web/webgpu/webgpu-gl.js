// A WebGL 2 context implemented on WebGPU (?webgpu): the subset of the WebGL 2 API that the game's GL
// emulation (GLEmu) uses, recorded into WebGPU command buffers. The game itself is unchanged; boot.js hands
// this object to it instead of canvas.getContext('webgl2').
//
// Shaders: each program's GLSL ES 3.00 is converted to Vulkan-style GLSL (glsl-vk.js) and from there to
// WGSL by naga (naga/, the shader translator of wgpu). Rendering follows OpenGL's conventions where they
// differ from WebGPU's: clip depth is mapped in the vertex stage, off-screen targets are rendered upside
// down so their rows are stored bottom-up as in OpenGL, viewports and scissors are given bottom-left.
//
// Not supported yet: reading pixels back (screenshots get black images), stencil, depth textures sampled
// by shaders, clears restricted by a scissor or a partial colour mask, copies and blits from the screen.
(function () {
  'use strict';

  const GPU_BUFFER = typeof GPUBufferUsage !== 'undefined' ? GPUBufferUsage : {};
  const GPU_TEXTURE = typeof GPUTextureUsage !== 'undefined' ? GPUTextureUsage : {};
  const GPU_STAGE = typeof GPUShaderStage !== 'undefined' ? GPUShaderStage : {};

  let device = null;
  let naga = null;
  let features = new Set();
  let canvasContext = null;
  let canvasFormat = 'bgra8unorm';
  let canvas = null;
  let adapterInfo = '';
  const warned = {};
  function warnOnce(key, msg) {
    if (warned[key]) return;
    warned[key] = true;
    console.warn('[WebGPU] ' + msg);
  }

  // ------------------------------------------------------------------ init

  async function init(theCanvas) {
    if (!navigator.gpu) throw new Error('WebGPU is not available in this browser');
    const adapter = await navigator.gpu.requestAdapter({ powerPreference: 'high-performance' });
    if (!adapter) throw new Error('No WebGPU adapter');
    const info = adapter.info || {};
    adapterInfo = [info.vendor, info.architecture, info.description].filter(Boolean).join(' ');
    // optional features shader packs' render targets use (32-bit float targets, R11F_G11F_B10F)
    const wanted = ['float32-filterable', 'float32-blendable', 'rg11b10ufloat-renderable'];
    const requiredFeatures = wanted.filter((f) => adapter.features.has(f));
    features = new Set(requiredFeatures);
    device = await adapter.requestDevice({
      requiredFeatures,
      requiredLimits: {
        maxTextureDimension2D: Math.min(8192, adapter.limits.maxTextureDimension2D),
        // shader packs bind up to 8 render targets and many samplers
        maxColorAttachments: Math.min(8, adapter.limits.maxColorAttachments),
        maxColorAttachmentBytesPerSample: adapter.limits.maxColorAttachmentBytesPerSample,
        maxSampledTexturesPerShaderStage: adapter.limits.maxSampledTexturesPerShaderStage,
        maxSamplersPerShaderStage: adapter.limits.maxSamplersPerShaderStage,
        maxBindingsPerBindGroup: adapter.limits.maxBindingsPerBindGroup,
        maxInterStageShaderVariables: adapter.limits.maxInterStageShaderVariables,
      },
    });
    let errors = 0;
    device.addEventListener('uncapturederror', (e) => {
      if (errors++ < 20) console.error('[WebGPU] ' + (e.error && e.error.message || e.error));
    });
    device.lost.then((i) => console.error('[WebGPU] device lost (' + i.reason + '): ' + i.message + ' after ' + JSON.stringify(stats)));
    const mod = await import(new URL('webgpu/naga/naga.js', location.href).href);
    await mod.default({ module_or_path: new URL('webgpu/naga/naga_bg.wasm', location.href) });
    naga = mod;
    canvas = theCanvas;
    canvasContext = canvas.getContext('webgpu');
    canvasFormat = navigator.gpu.getPreferredCanvasFormat();
    canvasContext.configure({
      device, format: canvasFormat, alphaMode: 'opaque',
      usage: GPU_TEXTURE.RENDER_ATTACHMENT | GPU_TEXTURE.COPY_SRC,
    });
    return adapterInfo;
  }

  // ------------------------------------------------------------------ constants

  const C = {
    DEPTH_BUFFER_BIT: 256, STENCIL_BUFFER_BIT: 1024, COLOR_BUFFER_BIT: 16384,
    POINTS: 0, LINES: 1, LINE_LOOP: 2, LINE_STRIP: 3, TRIANGLES: 4, TRIANGLE_STRIP: 5, TRIANGLE_FAN: 6,
    ARRAY_BUFFER: 34962, ELEMENT_ARRAY_BUFFER: 34963, COPY_READ_BUFFER: 36662, COPY_WRITE_BUFFER: 36663,
    PIXEL_PACK_BUFFER: 35051, PIXEL_UNPACK_BUFFER: 35052, UNIFORM_BUFFER: 35345,
    STREAM_DRAW: 35040, STATIC_DRAW: 35044, DYNAMIC_DRAW: 35048,
    BYTE: 5120, UNSIGNED_BYTE: 5121, SHORT: 5122, UNSIGNED_SHORT: 5123, INT: 5124, UNSIGNED_INT: 5125,
    FLOAT: 5126, HALF_FLOAT: 5131,
    TEXTURE_2D: 3553, TEXTURE0: 33984, TEXTURE_MAG_FILTER: 10240, TEXTURE_MIN_FILTER: 10241,
    TEXTURE_WRAP_S: 10242, TEXTURE_WRAP_T: 10243, TEXTURE_MIN_LOD: 33082, TEXTURE_MAX_LOD: 33083,
    TEXTURE_BASE_LEVEL: 33084, TEXTURE_MAX_LEVEL: 33085, TEXTURE_COMPARE_MODE: 34892, TEXTURE_COMPARE_FUNC: 34893,
    NEAREST: 9728, LINEAR: 9729, NEAREST_MIPMAP_NEAREST: 9984, LINEAR_MIPMAP_NEAREST: 9985,
    NEAREST_MIPMAP_LINEAR: 9986, LINEAR_MIPMAP_LINEAR: 9987, REPEAT: 10497, CLAMP_TO_EDGE: 33071,
    MIRRORED_REPEAT: 33648,
    FRAMEBUFFER: 36160, READ_FRAMEBUFFER: 36008, DRAW_FRAMEBUFFER: 36009, COLOR_ATTACHMENT0: 36064,
    DEPTH_ATTACHMENT: 36096, STENCIL_ATTACHMENT: 36128, DEPTH_STENCIL_ATTACHMENT: 33306,
    FRAMEBUFFER_COMPLETE: 36053,
    BLEND: 3042, CULL_FACE: 2884, DEPTH_TEST: 2929, SCISSOR_TEST: 3089, STENCIL_TEST: 2960,
    POLYGON_OFFSET_FILL: 32823, DITHER: 3024,
    FRONT: 1028, BACK: 1029, FRONT_AND_BACK: 1032, CW: 2304, CCW: 2305,
    RGBA: 6408, RGB: 6407, ALPHA: 6406, LUMINANCE: 6409, LUMINANCE_ALPHA: 6410, RED: 6403, RG: 33319,
    DEPTH_COMPONENT: 6402, BGRA: 32993,
    UNPACK_ALIGNMENT: 3317, UNPACK_ROW_LENGTH: 3314, UNPACK_SKIP_ROWS: 3315, UNPACK_SKIP_PIXELS: 3316,
    PACK_ALIGNMENT: 3333, PACK_ROW_LENGTH: 3330, PACK_SKIP_ROWS: 3331, PACK_SKIP_PIXELS: 3332,
    COMPILE_STATUS: 35713, LINK_STATUS: 35714, DELETE_STATUS: 35712, ACTIVE_UNIFORMS: 35718,
    ACTIVE_ATTRIBUTES: 35721, VERTEX_SHADER: 35633, FRAGMENT_SHADER: 35632,
    VENDOR: 7936, RENDERER: 7937, VERSION: 7938, SHADING_LANGUAGE_VERSION: 35724, MAX_TEXTURE_SIZE: 3379,
    MAX_TEXTURE_IMAGE_UNITS: 34930, MAX_COMBINED_TEXTURE_IMAGE_UNITS: 35661, MAX_VERTEX_ATTRIBS: 34921,
    MAX_DRAW_BUFFERS: 34852, MAX_COLOR_ATTACHMENTS: 36063, MAX_RENDERBUFFER_SIZE: 34024, VIEWPORT: 2978,
    MAX_VIEWPORT_DIMS: 3386, SCISSOR_BOX: 3088, NO_ERROR: 0,
  };
  // Every WebGL 2 constant (shaders and GLEmu read some, e.g. gl.RENDERER, from the context object).
  const CONSTANTS = {};
  if (typeof WebGL2RenderingContext !== 'undefined') {
    for (const k of Object.getOwnPropertyNames(WebGL2RenderingContext)) {
      if (/^[A-Z0-9_]+$/.test(k) && typeof WebGL2RenderingContext[k] === 'number') CONSTANTS[k] = WebGL2RenderingContext[k];
    }
  }
  Object.assign(CONSTANTS, C);

  const BLEND_FACTORS = {
    0: 'zero', 1: 'one', 768: 'src', 769: 'one-minus-src', 770: 'src-alpha', 771: 'one-minus-src-alpha',
    772: 'dst-alpha', 773: 'one-minus-dst-alpha', 774: 'dst', 775: 'one-minus-dst', 776: 'src-alpha-saturated',
    32769: 'constant', 32770: 'one-minus-constant', 32771: 'constant', 32772: 'one-minus-constant',
  };
  const BLEND_OPS = { 32774: 'add', 32778: 'subtract', 32779: 'reverse-subtract', 32775: 'min', 32776: 'max' };
  const COMPARE = {
    512: 'never', 513: 'less', 514: 'equal', 515: 'less-equal', 516: 'greater', 517: 'not-equal',
    518: 'greater-equal', 519: 'always',
  };

  // ------------------------------------------------------------------ frame: encoder, rings, flush

  let serial = 1; // increases with every submit; resources remember the serial of their last use
  let encoder = null;
  let pass = null; // { fb, encoder: GPURenderPassEncoder, width, height, flip, key, vp, sc, pipeline, ... }
  const pendingDestroy = [];
  let flushQueued = false;
  const stats = { draws: 0, passes: 0, submits: 0, pipelines: 0, frames: 0 };

  function enc() {
    if (!encoder) encoder = device.createCommandEncoder();
    if (!flushQueued) {
      // Safety net for work recorded outside a frame (the frame end normally flushes, see endFrame).
      flushQueued = true;
      setTimeout(() => { flushQueued = false; flush(); }, 0);
    }
    return encoder;
  }

  // Per-submit scratch buffers (streamed vertices, uniforms). A submit's buffer is not written again until
  // the GPU finished that submit: each submit takes a buffer from a small pool.
  class Ring {
    constructor(size, usage, label) {
      this.usage = usage;
      this.label = label;
      this.size = size;
      this.free = [];
      this.buffer = device.createBuffer({ size, usage, label });
      this.offset = 0;
    }
    /** Returns the offset of n bytes (aligned), or -1 when the ring is full for this submit. */
    alloc(n, align) {
      const off = Math.ceil(this.offset / align) * align;
      if (off + n > this.size) return -1;
      this.offset = off + n;
      return off;
    }
    grow(n) {
      this.size = Math.max(this.size * 2, Math.ceil(n * 1.5 / 256) * 256);
      for (const b of this.free) b.destroy();
      this.free = [];
      this.buffer = device.createBuffer({ size: this.size, usage: this.usage, label: this.label });
      this.offset = 0;
    }
    /** After a submit: the used buffer returns to the pool once the GPU is done with it. */
    rotate(done) {
      if (this.offset === 0) return;
      const used = this.buffer;
      const size = this.size;
      done.then(() => {
        if (used.size === this.size && this.free.length < 4) this.free.push(used);
        else used.destroy();
      });
      this.buffer = this.free.pop() || device.createBuffer({ size, usage: this.usage, label: this.label });
      this.offset = 0;
    }
    room() {
      return this.size - this.offset;
    }
  }
  let vertexRing = null;
  let uniformRing = null;

  /** Writes data into a ring and returns { buffer, offset }; may submit the frame so far to make room. */
  function ringWrite(ring, data, align) {
    const n = Math.ceil(data.byteLength / 4) * 4;
    let off = ring.alloc(n, align);
    if (off < 0) {
      flush();
      if (n > ring.size) ring.grow(n);
      off = ring.alloc(n, align);
    }
    if ((data.byteLength & 3) !== 0) {
      const padded = new Uint8Array(n);
      padded.set(new Uint8Array(data.buffer, data.byteOffset, data.byteLength));
      device.queue.writeBuffer(ring.buffer, off, padded);
    } else {
      device.queue.writeBuffer(ring.buffer, off, data.buffer, data.byteOffset, data.byteLength);
    }
    return { buffer: ring.buffer, offset: off };
  }

  function endPass() {
    if (pass) {
      pass.encoder.end();
      pass = null;
    }
  }

  /** Submits everything recorded so far. */
  function flush() {
    endPass();
    // clears requested on targets nothing was drawn to afterwards
    for (const fb of fbsWithPendingClears) {
      if (fb.pendingColors.some(Boolean) || fb.pendingDepth != null) {
        beginPass(fb);
        endPass();
      }
    }
    fbsWithPendingClears.clear();
    if (encoder) {
      device.queue.submit([encoder.finish()]);
      encoder = null;
      stats.submits++;
    }
    serial++;
    if (vertexRing.offset || uniformRing.offset) {
      const done = device.queue.onSubmittedWorkDone();
      vertexRing.rotate(done);
      uniformRing.rotate(done);
    }
    for (const r of pendingDestroy.splice(0)) r.destroy();
  }

  /** Called by boot.js when the game finished a frame (glfwSwapBuffers). */
  function endFrame() {
    if (!device) return;
    flush();
    stats.frames++;
  }

  // ------------------------------------------------------------------ objects

  let nextId = 1;
  class GLBuffer { constructor() { this.id = nextId++; this.gpu = null; this.offset = 0; this.size = 0; this.owned = false; this.lastUse = 0; this.cpu = null; } }
  class GLTexture {
    constructor() {
      this.id = nextId++;
      this.gpu = null;
      this.format = null;
      this.width = 0;
      this.height = 0;
      this.levels = 1;
      this.written = 0; // bit mask of levels with contents
      this.min = C.NEAREST_MIPMAP_LINEAR;
      this.mag = C.LINEAR;
      this.wrapS = C.REPEAT;
      this.wrapT = C.REPEAT;
      this.maxLevel = 1000;
      this.compareMode = 0;
      this.compareFunc = 515;
      this.generation = 0;
      this.lastUse = 0;
      this.view = null;
    }
  }
  class GLFramebuffer {
    constructor() {
      this.id = nextId++;
      this.color = [];
      this.depth = null;
      this.pendingColors = []; // clear colours per attachment index
      this.pendingDepth = null;
      this.drawBuffers = null; // glDrawBuffers; null: COLOR_ATTACHMENT0 only
    }
  }

  /** Whether colour attachment i of fb is among its draw buffers (location i writes attachment i). */
  function drawBufferOn(fb, i) {
    if (fb.isDefault) return i === 0;
    const db = fb.drawBuffers;
    return db ? db[i] === C.COLOR_ATTACHMENT0 + i : i === 0;
  }
  class GLShader { constructor(type) { this.id = nextId++; this.type = type; this.source = ''; } }
  class GLProgram {
    constructor() {
      this.id = nextId++;
      this.shaders = [];
      this.bindings = {};
      this.linked = false;
      this.log = '';
      this.conv = null;
      this.variants = new Map();
      this.pipelines = new Map();
      this.bindGroups = new Map();
    }
  }

  const DEFAULT_FB = new GLFramebuffer();
  DEFAULT_FB.isDefault = true;
  const fbsWithPendingClears = new Set();
  let canvasDepth = null;

  // ------------------------------------------------------------------ state

  const st = {
    arrayBuffer: null, elementBuffer: null, copyRead: null, copyWrite: null,
    activeUnit: 0, units: new Array(16).fill(null),
    program: null, drawFb: DEFAULT_FB, readFb: DEFAULT_FB,
    blend: false, srcRGB: 1, dstRGB: 0, srcA: 1, dstA: 0, eqRGB: 32774, eqA: 32774, blendColor: [0, 0, 0, 0],
    depthTest: false, depthFunc: 513, depthMask: true,
    cull: false, cullFace: C.BACK, frontFace: C.CCW,
    colorMask: [true, true, true, true],
    offsetFill: false, offsetFactor: 0, offsetUnits: 0,
    scissorTest: false, scissor: [0, 0, 0, 0], viewport: [0, 0, 0, 0],
    clearColor: [0, 0, 0, 0], clearDepth: 1,
    unpackAlignment: 4, unpackRowLength: 0, unpackSkipRows: 0, unpackSkipPixels: 0,
    attribs: [],
    generic: [],
  };
  for (let i = 0; i < 16; i++) {
    st.attribs.push({ enabled: false, buffer: null, size: 4, type: C.FLOAT, normalized: false, stride: 0, offset: 0, divisor: 0 });
    st.generic.push(new Float32Array([0, 0, 0, 1]));
  }

  // ------------------------------------------------------------------ render targets

  function ensureCanvasDepth() {
    const w = canvas.width;
    const h = canvas.height;
    if (!canvasDepth || canvasDepth.width !== w || canvasDepth.height !== h) {
      if (canvasDepth) pendingDestroy.push(canvasDepth.texture);
      canvasDepth = {
        width: w, height: h,
        texture: device.createTexture({ size: [w, h], format: 'depth24plus-stencil8', usage: GPU_TEXTURE.RENDER_ATTACHMENT }),
      };
      canvasDepth.view = canvasDepth.texture.createView();
    }
    return canvasDepth;
  }

  /** Attachment views and formats of a framebuffer, or null when it cannot be drawn to. */
  function targets(fb) {
    if (fb.isDefault) {
      const depth = ensureCanvasDepth();
      return {
        colors: [{ view: canvasContext.getCurrentTexture().createView(), format: canvasFormat }],
        depth: { view: depth.view, format: 'depth24plus-stencil8' },
        width: canvas.width, height: canvas.height, flip: 1,
        key: 'canvas',
      };
    }
    const colors = [];
    let width = 0;
    let height = 0;
    let key = 'fb' + fb.id;
    for (let i = 0; i < 8; i++) {
      const a = fb.color[i];
      if (!a || !a.tex.gpu) continue;
      colors[i] = { view: attachmentView(a.tex, a.level), format: a.tex.format };
      width = Math.max(1, a.tex.width >> a.level);
      height = Math.max(1, a.tex.height >> a.level);
      key += '|c' + i + ':' + a.tex.id + '.' + a.tex.generation + '.' + a.level + a.tex.format;
      a.tex.lastUse = serial;
    }
    let depth = null;
    if (fb.depth && fb.depth.tex.gpu) {
      const t = fb.depth.tex;
      depth = { view: attachmentView(t, fb.depth.level), format: t.format };
      if (!width) {
        width = t.width;
        height = t.height;
      }
      key += '|d:' + t.id + '.' + t.generation + t.format;
      t.lastUse = serial;
    }
    if (!width) return null;
    return { colors, depth, width, height, flip: -1, key };
  }

  function attachmentView(tex, level) {
    tex.attachViews = tex.attachViews || {};
    const k = level + ':' + tex.generation;
    if (!tex.attachViews[k]) tex.attachViews[k] = tex.gpu.createView({ baseMipLevel: level, mipLevelCount: 1 });
    return tex.attachViews[k];
  }

  let canvasFlushQueued = false;

  function beginPass(fb) {
    const t = targets(fb);
    if (!t) return false;
    if (fb.isDefault && !canvasFlushQueued) {
      // The canvas texture is presented, and invalid, once the current task ends: submit before that even if
      // the frame continues in a later task (the game's green threads can yield in the middle of a frame).
      canvasFlushQueued = true;
      queueMicrotask(() => {
        canvasFlushQueued = false;
        flush();
      });
    }
    const colorAttachments = [];
    for (let i = 0; i < t.colors.length; i++) {
      const c = t.colors[i];
      if (!c) {
        colorAttachments.push(null);
        continue;
      }
      const clear = fb.pendingColors[i];
      colorAttachments.push({
        view: c.view,
        loadOp: clear ? 'clear' : 'load',
        clearValue: clear ? { r: clear[0], g: clear[1], b: clear[2], a: clear[3] } : undefined,
        storeOp: 'store',
      });
    }
    const desc = { colorAttachments };
    if (t.depth) {
      const clear = fb.pendingDepth;
      desc.depthStencilAttachment = {
        view: t.depth.view,
        depthLoadOp: clear != null ? 'clear' : 'load',
        depthClearValue: clear != null ? clear : 1,
        depthStoreOp: 'store',
      };
      if (/stencil/.test(t.depth.format)) {
        desc.depthStencilAttachment.stencilLoadOp = 'load';
        desc.depthStencilAttachment.stencilStoreOp = 'store';
      }
    }
    fb.pendingColors = [];
    fb.pendingDepth = null;
    endPass();
    pass = {
      fb, encoder: enc().beginRenderPass(desc), width: t.width, height: t.height, flip: t.flip, key: t.key,
      colorFormats: t.colors.map((c) => (c ? c.format : null)), depthFormat: t.depth ? t.depth.format : null,
      vp: '', sc: '', pipeline: null, blendConstant: '',
    };
    stats.passes++;
    return true;
  }

  function ensurePass() {
    const fb = st.drawFb;
    if (pass && pass.fb === fb) {
      if (fb.isDefault) return true;
      const t = targets(fb); // attachments may have changed since the pass began
      if (t && t.key === pass.key) return true;
    }
    return beginPass(fb);
  }

  // ------------------------------------------------------------------ textures

  const SAMPLERS = new Map();
  function gpuSampler(tex, comparison) {
    const mipmapped = tex.min >= C.NEAREST_MIPMAP_NEAREST && tex.min <= C.LINEAR_MIPMAP_LINEAR;
    let lodMax = 0;
    if (mipmapped) {
      let highest = 0;
      while (highest + 1 < tex.levels && (tex.written & (1 << (highest + 1)))) highest++;
      lodMax = Math.min(tex.maxLevel, highest);
    }
    const key = tex.min + '|' + tex.mag + '|' + tex.wrapS + '|' + tex.wrapT + '|' + lodMax + '|' + (comparison ? tex.compareFunc : 0);
    let s = SAMPLERS.get(key);
    if (!s) {
      const wrap = (w) => (w === C.CLAMP_TO_EDGE || w === 10496 || w === 33069 ? 'clamp-to-edge' : w === C.MIRRORED_REPEAT ? 'mirror-repeat' : 'repeat');
      const desc = {
        addressModeU: wrap(tex.wrapS), addressModeV: wrap(tex.wrapT),
        magFilter: tex.mag === C.NEAREST ? 'nearest' : 'linear',
        minFilter: tex.min === C.NEAREST || tex.min === C.NEAREST_MIPMAP_NEAREST || tex.min === C.NEAREST_MIPMAP_LINEAR ? 'nearest' : 'linear',
        mipmapFilter: tex.min === C.NEAREST_MIPMAP_LINEAR || tex.min === C.LINEAR_MIPMAP_LINEAR ? 'linear' : 'nearest',
        lodMinClamp: 0, lodMaxClamp: lodMax,
      };
      if (comparison) desc.compare = COMPARE[tex.compareFunc] || 'less-equal';
      s = device.createSampler(desc);
      SAMPLERS.set(key, s);
    }
    return s;
  }

  function mipCount(w, h) {
    return Math.floor(Math.log2(Math.max(w, h))) + 1;
  }

  function gpuFormat(internal, format, type) {
    if (internal === 35056 || internal === 36013 || format === 34041) return 'depth24plus-stencil8'; // DEPTH(32F)_STENCIL
    if (format === C.DEPTH_COMPONENT || internal === 33189 || internal === 33190 || internal === 33191 || internal === 36012) {
      return 'depth32float';
    }
    // 32-bit float targets need two optional features to be filtered and blended; otherwise half floats
    const f32 = features.has('float32-filterable') && features.has('float32-blendable');
    switch (internal) {
      case 33321: return 'r8unorm'; // R8
      case 33323: return 'rg8unorm'; // RG8
      case 33325: return 'r16float'; // R16F
      case 33326: return f32 ? 'r32float' : 'r16float'; // R32F
      case 33327: return 'rg16float'; // RG16F
      case 33328: return f32 ? 'rg32float' : 'rg16float'; // RG32F
      case 34842: case 34843: return 'rgba16float'; // RGBA16F, RGB16F
      case 34836: case 34837: return f32 ? 'rgba32float' : 'rgba16float'; // RGBA32F, RGB32F
      case 35898: return features.has('rg11b10ufloat-renderable') ? 'rg11b10ufloat' : 'rgba16float'; // R11F_G11F_B10F
      case 32857: return 'rgb10a2unorm'; // RGB10_A2
      default: break;
    }
    if (internal === C.RED && format === C.RED) return 'r8unorm';
    return 'rgba8unorm';
  }

  const isDepthFormat = (f) => /^depth/.test(f);

  function createGpuTexture(tex, levels) {
    const depth = /^depth/.test(tex.format);
    const old = tex.gpu;
    tex.levels = levels;
    tex.gpu = device.createTexture({
      size: [tex.width, tex.height], format: tex.format, mipLevelCount: levels,
      usage: GPU_TEXTURE.TEXTURE_BINDING | GPU_TEXTURE.RENDER_ATTACHMENT | (depth ? 0 : GPU_TEXTURE.COPY_DST | GPU_TEXTURE.COPY_SRC),
      label: 'texture ' + tex.id + '.' + (tex.generation + 1),
    });
    tex.generation++;
    tex.view = null;
    tex.attachViews = null;
    return old;
  }

  /** Converts upload data to the GPU format's layout (RGB, luminance and alpha become RGBA). */
  function convertPixels(src, width, height, format, type, rowStride) {
    const comps = format === C.RGB ? 3 : format === C.LUMINANCE_ALPHA || format === C.RG ? 2 : format === C.RGBA || format === C.BGRA ? 4 : 1;
    if (type !== C.UNSIGNED_BYTE && !(type === 33639 || type === 32821)) {
      warnOnce('type' + type, 'texture data type ' + type + ' is not supported');
      return null;
    }
    const out = new Uint8Array(width * height * 4);
    for (let y = 0; y < height; y++) {
      let s = y * rowStride;
      let d = y * width * 4;
      for (let x = 0; x < width; x++, s += (type === C.UNSIGNED_BYTE ? comps : 4), d += 4) {
        if (format === C.RGBA) { out[d] = src[s]; out[d + 1] = src[s + 1]; out[d + 2] = src[s + 2]; out[d + 3] = src[s + 3]; }
        else if (format === C.BGRA) { out[d] = src[s + 2]; out[d + 1] = src[s + 1]; out[d + 2] = src[s]; out[d + 3] = src[s + 3]; }
        else if (format === C.RGB) { out[d] = src[s]; out[d + 1] = src[s + 1]; out[d + 2] = src[s + 2]; out[d + 3] = 255; }
        else if (format === C.LUMINANCE) { out[d] = out[d + 1] = out[d + 2] = src[s]; out[d + 3] = 255; }
        else if (format === C.LUMINANCE_ALPHA) { out[d] = out[d + 1] = out[d + 2] = src[s]; out[d + 3] = src[s + 1]; }
        else if (format === C.ALPHA) { out[d] = out[d + 1] = out[d + 2] = 0; out[d + 3] = src[s]; }
        else { out[d] = src[s]; out[d + 1] = 0; out[d + 2] = 0; out[d + 3] = 255; }
      }
    }
    return out;
  }

  function upload(tex, level, x, y, width, height, format, type, data) {
    if (!data || !tex.gpu || /^depth/.test(tex.format) || width <= 0 || height <= 0) return;
    const bytes = data instanceof Uint8Array ? data : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
    const comps = format === C.RGB ? 3 : format === C.LUMINANCE_ALPHA || format === C.RG ? 2 : format === C.RGBA || format === C.BGRA ? 4 : 1;
    const bpp = type === C.UNSIGNED_BYTE ? comps : 4;
    const rowLength = st.unpackRowLength > 0 ? st.unpackRowLength : width;
    const align = st.unpackAlignment || 1;
    const rowStride = Math.ceil(rowLength * bpp / align) * align;
    const start = st.unpackSkipRows * rowStride + st.unpackSkipPixels * bpp;
    if (tex.lastUse === serial) flush(); // keep the order: commands recorded so far read the old contents
    let src;
    let bytesPerRow;
    let offset = 0;
    if (tex.format === 'rgba8unorm' && format === C.RGBA && type === C.UNSIGNED_BYTE) {
      src = bytes;
      bytesPerRow = rowStride;
      offset = start;
    } else if (tex.format === 'r8unorm' && type === C.UNSIGNED_BYTE && comps === 1) {
      src = bytes;
      bytesPerRow = rowStride;
      offset = start;
    } else {
      src = convertPixels(bytes.subarray(start), width, height, format, type, rowStride);
      if (!src) return;
      bytesPerRow = width * 4;
    }
    device.queue.writeTexture({ texture: tex.gpu, mipLevel: level, origin: { x, y } }, src,
      { offset, bytesPerRow, rowsPerImage: height }, { width, height });
    tex.written |= 1 << level;
  }

  // ------------------------------------------------------------------ programs

  const MAIN_BLIT = `
struct U { rect: vec4f, uv: vec4f, flip: vec4f }
@group(0) @binding(0) var<uniform> u: U;
@group(0) @binding(1) var t: texture_2d<f32>;
@group(0) @binding(2) var s: sampler;
struct VO { @builtin(position) p: vec4f, @location(0) uv: vec2f }
@vertex fn vs(@builtin(vertex_index) i: u32) -> VO {
  let c = vec2f(f32(i & 1u), f32((i >> 1u) & 1u));
  var o: VO;
  let ndc = mix(u.rect.xy, u.rect.zw, c);
  o.p = vec4f(ndc.x, ndc.y * u.flip.x, 0.0, 1.0);
  o.uv = mix(u.uv.xy, u.uv.zw, c);
  return o;
}
@fragment fn fs(i: VO) -> @location(0) vec4f { return textureSample(t, s, i.uv); }`;

  function wgsl(src, stage) {
    const m = naga.parseGlsl(src, { stage });
    try {
      const info = naga.validate(m);
      try {
        // Shader packs sample textures inside branches that depend on varyings; WGSL rejects implicit
        // derivatives in non-uniform control flow unless this check is off (GLSL leaves it undefined).
        return 'diagnostic(off, derivative_uniformity);\n' + naga.writeWgsl(m, info, {});
      } finally {
        info.free();
      }
    } finally {
      m.free();
    }
  }

  /** The shader modules of a program for the given integer attribute classes ({location: 'i'|'u'}). */
  function variant(prog, classes) {
    const key = JSON.stringify(classes);
    let v = prog.variants.get(key);
    if (v) return v;
    const conv = TFGlslVk.convertProgram(prog.vsSource, prog.fsSource, prog.bindings, classes);
    v = {
      vs: device.createShaderModule({ code: wgsl(conv.vs, 'vertex'), label: 'program ' + prog.id + ' vs' }),
      fs: device.createShaderModule({ code: wgsl(conv.fs, 'fragment'), label: 'program ' + prog.id + ' fs' }),
    };
    prog.variants.set(key, v);
    return v;
  }

  function linkProgram(prog) {
    const vs = prog.shaders.find((s) => s.type === C.VERTEX_SHADER);
    const fs = prog.shaders.find((s) => s.type === C.FRAGMENT_SHADER);
    prog.linked = false;
    prog.textureGroups = null;
    prog.variants.clear();
    prog.pipelines.clear();
    prog.bindGroups.clear();
    if (!vs || !fs) {
      prog.log = 'missing shader';
      return;
    }
    try {
      prog.vsSource = vs.source;
      prog.fsSource = fs.source;
      const conv = TFGlslVk.convertProgram(vs.source, fs.source, prog.bindings, {});
      prog.conv = conv;
      variant(prog, {});
      prog.data = new ArrayBuffer(conv.uniformSize);
      prog.f32 = new Float32Array(prog.data);
      prog.i32 = new Int32Array(prog.data);
      prog.uniformByName = {};
      for (const u of conv.uniforms) prog.uniformByName[u.name] = u;
      prog.samplerUnits = conv.samplers.map(() => 0);
      prog.samplerByName = {};
      conv.samplers.forEach((s, i) => { prog.samplerByName[s.name] = i; });
      prog.attribByName = {};
      for (const a of conv.attributes) prog.attribByName[a.name] = a;
      prog.flipU = prog.uniformByName.tf_flipY;
      prog.f32[prog.flipU.offset / 4] = 1;
      prog.dirty = true;
      prog.slice = null;
      const vis = GPU_STAGE.VERTEX | GPU_STAGE.FRAGMENT;
      prog.layout0 = device.createBindGroupLayout({
        entries: [{ binding: 0, visibility: vis, buffer: { type: 'uniform', hasDynamicOffset: true, minBindingSize: conv.uniformSize } }],
      });
      prog.layouts = new Map(); // per depth-as-colour mask, see layoutFor
      prog.linked = true;
      prog.log = '';
    } catch (e) {
      prog.log = String(e && (e.formatted || e.message) || e);
      console.warn('[WebGPU] program ' + prog.id + ' failed: ' + prog.log.split('\n').slice(0, 6).join(' | '));
    }
  }

  // ------------------------------------------------------------------ vertex formats

  function vertexFormat(type, size, normalized) {
    const n2 = size <= 2 ? 'x2' : 'x4'; // 8- and 16-bit formats come in pairs and fours
    const k = size <= 2 ? 2 : 4;
    switch (type) {
      case C.FLOAT: return { format: 'float32' + (size === 1 ? '' : 'x' + size), cls: 'f', bytes: 4 * size };
      case C.HALF_FLOAT: return { format: 'float16' + n2, cls: 'f', bytes: 2 * k };
      case C.UNSIGNED_BYTE: return normalized ? { format: 'unorm8' + n2, cls: 'f', bytes: k } : { format: 'uint8' + n2, cls: 'u', bytes: k };
      case C.BYTE: return normalized ? { format: 'snorm8' + n2, cls: 'f', bytes: k } : { format: 'sint8' + n2, cls: 'i', bytes: k };
      case C.UNSIGNED_SHORT: return normalized ? { format: 'unorm16' + n2, cls: 'f', bytes: 2 * k } : { format: 'uint16' + n2, cls: 'u', bytes: 2 * k };
      case C.SHORT: return normalized ? { format: 'snorm16' + n2, cls: 'f', bytes: 2 * k } : { format: 'sint16' + n2, cls: 'i', bytes: 2 * k };
      case C.INT: return { format: 'sint32' + (size === 1 ? '' : 'x' + size), cls: 'i', bytes: 4 * size };
      case C.UNSIGNED_INT: return { format: 'uint32' + (size === 1 ? '' : 'x' + size), cls: 'u', bytes: 4 * size };
      default: return { format: 'float32x4', cls: 'f', bytes: 16 };
    }
  }

  // ------------------------------------------------------------------ draws

  function topologyOf(mode) {
    switch (mode) {
      case C.POINTS: return 'point-list';
      case C.LINES: return 'line-list';
      case C.LINE_STRIP: return 'line-strip';
      case C.TRIANGLE_STRIP: return 'triangle-strip';
      default: return 'triangle-list';
    }
  }

  function blendState() {
    if (!st.blend) return null;
    const comp = (src, dst, eq) => {
      const op = BLEND_OPS[eq] || 'add';
      return op === 'min' || op === 'max' ? { operation: op, srcFactor: 'one', dstFactor: 'one' }
        : { operation: op, srcFactor: BLEND_FACTORS[src] || 'one', dstFactor: BLEND_FACTORS[dst] || 'zero' };
    };
    return { color: comp(st.srcRGB, st.dstRGB, st.eqRGB), alpha: comp(st.srcA, st.dstA, st.eqA) };
  }

  function draw(mode, count, indexType, indexOffset, instances) {
    const prog = st.program;
    if (!prog || !prog.linked || count <= 0) return;
    // room for this draw's scratch data, so that nothing submits while the pass is being recorded
    if (vertexRing.room() < (1 << 20) + count * 12 || uniformRing.room() < 65536) flush();
    if (!ensurePass()) return;
    const p = pass;

    // vertex inputs: enabled arrays grouped into vertex buffers, generic values as stride-0 buffers
    const classes = {};
    const slots = [];
    const slotBindings = [];
    let sig = '';
    const bySource = new Map();
    for (const a of prog.conv.attributes) {
      const loc = a.location;
      const s = st.attribs[loc];
      if (s.enabled && s.buffer && s.buffer.gpu) {
        const f = vertexFormat(s.type, s.size, s.normalized);
        if (f.cls !== 'f') classes[loc] = f.cls;
        const stride = s.stride || f.bytes;
        const key = s.buffer.id + ':' + stride + ':' + s.divisor;
        let group = bySource.get(key);
        if (!group) {
          group = { buffer: s.buffer, stride, divisor: s.divisor, attrs: [] };
          bySource.set(key, group);
        }
        group.attrs.push({ loc, format: f.format, offset: s.offset, bytes: f.bytes });
        s.buffer.lastUse = serial;
      } else {
        const g = st.generic[loc];
        const r = ringWrite(vertexRing, g, 16);
        slots.push({ arrayStride: 0, stepMode: 'vertex', attributes: [{ shaderLocation: loc, format: 'float32x4', offset: 0 }] });
        slotBindings.push(r);
        sig += 'g' + loc + ';';
      }
    }
    for (const group of bySource.values()) {
      group.attrs.sort((x, y) => x.offset - y.offset);
      let i = 0;
      while (i < group.attrs.length) {
        const base = group.attrs[i].offset;
        const attributes = [];
        while (i < group.attrs.length && group.attrs[i].offset - base + group.attrs[i].bytes <= Math.max(group.stride, group.attrs[i].bytes)
          && (attributes.length === 0 || group.attrs[i].offset - base < group.stride)) {
          const at = group.attrs[i];
          attributes.push({ shaderLocation: at.loc, format: at.format, offset: at.offset - base });
          i++;
        }
        if (!attributes.length) { // cannot happen, but never loop forever
          const at = group.attrs[i++];
          attributes.push({ shaderLocation: at.loc, format: at.format, offset: 0 });
        }
        const stride = Math.ceil(group.stride / 4) * 4;
        slots.push({ arrayStride: stride, stepMode: group.divisor > 0 ? 'instance' : 'vertex', attributes });
        slotBindings.push({ buffer: group.buffer.gpu, offset: group.buffer.offset + base });
        sig += stride + (group.divisor > 0 ? 'i' : 'v') + attributes.map((a) => a.shaderLocation + a.format + '@' + a.offset).join(',') + ';';
      }
    }
    if (slots.length > 8) {
      warnOnce('slots', 'more than 8 vertex buffers in one draw');
      return;
    }

    // topology; fans and loops become indexed lists
    let topology = topologyOf(mode);
    let index = null;
    let indexFormat = indexType === C.UNSIGNED_SHORT ? 'uint16' : 'uint32';
    let drawCount = count;
    if (indexType) {
      const eb = st.elementBuffer;
      if (!eb || !eb.gpu) return;
      if (indexType === C.UNSIGNED_BYTE) {
        if (!eb.cpu) return;
        const src = eb.cpu.subarray(indexOffset, indexOffset + count);
        index = ringWrite(vertexRing, Uint32Array.from(src), 4);
        indexFormat = 'uint32';
      } else {
        index = { buffer: eb.gpu, offset: eb.offset + indexOffset };
      }
      eb.lastUse = serial;
      if (mode === C.TRIANGLE_FAN || mode === C.LINE_LOOP) {
        warnOnce('idxfan', 'indexed triangle fans / line loops are not supported');
        return;
      }
    } else if (mode === C.TRIANGLE_FAN && count >= 3) {
      const idx = new Uint32Array((count - 2) * 3);
      for (let i = 0; i < count - 2; i++) { idx[i * 3] = 0; idx[i * 3 + 1] = i + 1; idx[i * 3 + 2] = i + 2; }
      index = ringWrite(vertexRing, idx, 4);
      indexFormat = 'uint32';
      drawCount = idx.length;
      topology = 'triangle-list';
    } else if (mode === C.LINE_LOOP && count >= 2) {
      const idx = new Uint32Array(count * 2);
      for (let i = 0; i < count; i++) { idx[i * 2] = i; idx[i * 2 + 1] = (i + 1) % count; }
      index = ringWrite(vertexRing, idx, 4);
      indexFormat = 'uint32';
      drawCount = idx.length;
      topology = 'line-list';
    }

    // textures bound to the program's samplers; a depth texture read through a colour sampler (shader packs
    // read depthtex0 as sampler2D) needs an unfilterable-float binding, so the layout depends on them
    const texs = boundTextures(prog);

    // pipeline
    const blend = blendState();
    const triangles = /triangle/.test(topology);
    const flip = p.flip;
    const pkey = p.colorFormats.join(',') + '|' + p.depthFormat + '|' + sig + '|' + JSON.stringify(classes) + '|' + topology
      + (index && /strip/.test(topology) ? indexFormat : '') + '|' + (blend ? JSON.stringify(blend) : '-') + '|'
      + st.colorMask.join('') + '|' + (st.depthTest ? st.depthFunc : 'off') + (st.depthMask ? 'w' : 'r') + '|'
      + (st.cull ? st.cullFace : 0) + st.frontFace + flip + '|'
      + (st.offsetFill && triangles ? st.offsetFactor + ',' + st.offsetUnits : '')
      + '|' + p.colorFormats.map((f, i) => (drawBufferOn(p.fb, i) ? 1 : 0)).join('') + '|' + texs.mask;
    let pipeline = prog.pipelines.get(pkey);
    if (!pipeline) {
      let v;
      try {
        v = variant(prog, classes);
      } catch (e) {
        warnOnce('variant' + prog.id + JSON.stringify(classes), 'program ' + prog.id + ' variant failed: ' + (e.formatted || e.message || e));
        return;
      }
      const writeMask = (st.colorMask[0] ? 1 : 0) | (st.colorMask[1] ? 2 : 0) | (st.colorMask[2] ? 4 : 0) | (st.colorMask[3] ? 8 : 0);
      // every attachment of the pass needs a target; those the shader does not write are masked off
      const fragTargets = p.colorFormats.map((f, i) => (f ? { format: f, blend: blend || undefined,
        writeMask: i < Math.max(1, prog.conv.fragmentOutputs) && drawBufferOn(p.fb, i) ? writeMask : 0 } : null));
      // OpenGL's front faces are counter-clockwise in window space; drawing upside down swaps them.
      const ccw = st.frontFace === C.CCW;
      const desc = {
        layout: layoutFor(prog, texs.mask).pipeline,
        vertex: { module: v.vs, entryPoint: 'main', buffers: slots },
        fragment: { module: v.fs, entryPoint: 'main', targets: fragTargets },
        primitive: {
          topology,
          stripIndexFormat: index && /strip/.test(topology) ? indexFormat : undefined,
          frontFace: (flip > 0) === ccw ? 'ccw' : 'cw',
          cullMode: st.cull ? (st.cullFace === C.FRONT ? 'front' : st.cullFace === C.BACK ? 'back' : 'none') : 'none',
        },
      };
      if (p.depthFormat) {
        desc.depthStencil = {
          format: p.depthFormat,
          depthWriteEnabled: st.depthTest && st.depthMask,
          depthCompare: st.depthTest ? COMPARE[st.depthFunc] || 'less' : 'always',
        };
        if (st.offsetFill && triangles) {
          desc.depthStencil.depthBias = Math.round(st.offsetUnits);
          desc.depthStencil.depthBiasSlopeScale = st.offsetFactor;
        }
      }
      try {
        pipeline = device.createRenderPipeline(desc);
      } catch (e) {
        warnOnce('pipe' + pkey, 'pipeline failed: ' + e.message);
        return;
      }
      prog.pipelines.set(pkey, pipeline);
      stats.pipelines++;
    }
    if (p.pipeline !== pipeline) {
      p.encoder.setPipeline(pipeline);
      p.pipeline = pipeline;
    }

    // uniforms (with the y flip of this target)
    const fi = prog.flipU.offset / 4;
    if (prog.f32[fi] !== flip) {
      prog.f32[fi] = flip;
      prog.dirty = true;
    }
    if (prog.dirty || !prog.slice || prog.slice.serial !== serial || prog.slice.ring !== uniformRing.buffer) {
      const r = ringWrite(uniformRing, new Uint8Array(prog.data), 256);
      prog.slice = { serial, offset: r.offset, ring: uniformRing.buffer };
      prog.dirty = false;
    }
    let g0 = prog.bindGroups.get(uniformRing.buffer);
    if (!g0) {
      g0 = device.createBindGroup({ layout: prog.layout0, entries: [{ binding: 0, resource: { buffer: uniformRing.buffer, size: prog.conv.uniformSize } }] });
      if (prog.bindGroups.size > 8) prog.bindGroups.clear();
      prog.bindGroups.set(uniformRing.buffer, g0);
    }
    p.encoder.setBindGroup(0, g0, [prog.slice.offset]);

    // textures
    if (prog.conv.samplers.length) {
      const entries = [];
      let key = texs.mask + '/';
      prog.conv.samplers.forEach((s, i) => {
        const tex = texs.list[i];
        const asColor = texs.mask[i] === 'u';
        tex.lastUse = serial;
        const view = textureView(tex);
        const sampler = asColor ? nearestSampler(tex) : gpuSampler(tex, s.sampleType === 'depth');
        entries.push({ binding: s.binding, resource: view });
        entries.push({ binding: s.binding + 1, resource: sampler });
        key += tex.id + '.' + tex.generation + ':' + SAMPLER_IDS(sampler) + ';';
      });
      let g1 = prog.textureGroups && prog.textureGroups.get(key);
      if (!g1) {
        g1 = device.createBindGroup({ layout: layoutFor(prog, texs.mask).group, entries });
        prog.textureGroups = prog.textureGroups || new Map();
        if (prog.textureGroups.size > 256) prog.textureGroups.clear();
        prog.textureGroups.set(key, g1);
      }
      p.encoder.setBindGroup(1, g1);
    }

    for (let i = 0; i < slotBindings.length; i++) p.encoder.setVertexBuffer(i, slotBindings[i].buffer, slotBindings[i].offset);

    // viewport and scissor, bottom-left in OpenGL; WebGPU's origin is the top-left corner
    const vp = st.viewport;
    const vx = Math.max(0, vp[0]);
    const vw = Math.max(0, Math.min(p.width - vx, vp[2]));
    const vyGL = Math.max(0, vp[1]);
    const vh = Math.max(0, Math.min(p.height - vyGL, vp[3]));
    const vy = flip > 0 ? p.height - vyGL - vh : vyGL;
    const vkey = vx + ',' + vy + ',' + vw + ',' + vh;
    if (vw <= 0 || vh <= 0) return;
    if (p.vp !== vkey) {
      p.encoder.setViewport(vx, vy, vw, vh, 0, 1);
      p.vp = vkey;
    }
    let sx = 0;
    let sy = 0;
    let sw = p.width;
    let sh = p.height;
    if (st.scissorTest) {
      const s = st.scissor;
      sx = Math.min(p.width, Math.max(0, s[0]));
      const syGL = Math.min(p.height, Math.max(0, s[1]));
      sw = Math.max(0, Math.min(p.width - sx, s[2]));
      sh = Math.max(0, Math.min(p.height - syGL, s[3]));
      sy = flip > 0 ? p.height - syGL - sh : syGL;
    }
    const skey = sx + ',' + sy + ',' + sw + ',' + sh;
    if (p.sc !== skey) {
      p.encoder.setScissorRect(sx, sy, sw, sh);
      p.sc = skey;
    }
    if (sw <= 0 || sh <= 0) return;
    const bc = st.blendColor.join(',');
    if (blend && p.blendConstant !== bc) {
      p.encoder.setBlendConstant({ r: st.blendColor[0], g: st.blendColor[1], b: st.blendColor[2], a: st.blendColor[3] });
      p.blendConstant = bc;
    }

    if (index) {
      p.encoder.setIndexBuffer(index.buffer, indexFormat, index.offset);
      p.encoder.drawIndexed(drawCount, instances || 1);
    } else {
      p.encoder.draw(drawCount, instances || 1);
    }
    stats.draws++;
  }

  /** The textures for a program's samplers, and per sampler 'u' where a depth texture is read as colour. */
  function boundTextures(prog) {
    const list = [];
    let mask = '';
    prog.conv.samplers.forEach((s, i) => {
      let tex = st.units[prog.samplerUnits[i]] || null;
      if (!tex || !tex.gpu) tex = dummyTexture();
      if (pass && attachedToPass(tex)) {
        // sampling a texture the pass draws into (undefined in OpenGL, an error in WebGPU)
        warnOnce('feedback' + prog.id, 'program ' + prog.id + ' samples ' + s.name + ', which it draws into; it reads a placeholder');
        tex = isDepthFormat(tex.format) ? dummyDepthTexture() : dummyTexture();
      }
      const depthAsColor = s.sampleType !== 'depth' && isDepthFormat(tex.format);
      if (s.sampleType === 'depth' && !isDepthFormat(tex.format)) tex = dummyDepthTexture();
      list.push(tex);
      mask += depthAsColor ? 'u' : '-';
    });
    return { list, mask };
  }

  function attachedToPass(tex) {
    const fb = pass.fb;
    if (fb.isDefault) return false;
    if (fb.depth && fb.depth.tex === tex) return true;
    for (const a of fb.color) if (a && a.tex === tex) return true;
    return false;
  }

  /** Bind group and pipeline layouts of a program for a depth-as-colour mask. */
  function layoutFor(prog, mask) {
    prog.layouts = prog.layouts || new Map();
    let l = prog.layouts.get(mask);
    if (l) return l;
    const vis = GPU_STAGE.VERTEX | GPU_STAGE.FRAGMENT;
    const layouts = [prog.layout0];
    let group = null;
    if (prog.conv.samplers.length) {
      const entries = [];
      prog.conv.samplers.forEach((s, i) => {
        const depth = s.sampleType === 'depth';
        const unfilterable = mask[i] === 'u';
        entries.push({ binding: s.binding, visibility: vis, texture: { sampleType: depth ? 'depth' : unfilterable ? 'unfilterable-float' : 'float', viewDimension: s.viewDimension } });
        entries.push({ binding: s.binding + 1, visibility: vis, sampler: { type: depth ? 'comparison' : unfilterable ? 'non-filtering' : 'filtering' } });
      });
      group = device.createBindGroupLayout({ entries });
      layouts.push(group);
    }
    l = { group, pipeline: device.createPipelineLayout({ bindGroupLayouts: layouts }) };
    prog.layouts.set(mask, l);
    return l;
  }

  /** The view a texture is sampled through (the depth aspect of a depth-stencil texture). */
  function textureView(tex) {
    if (!tex.view) tex.view = tex.gpu.createView(/stencil/.test(tex.format) ? { aspect: 'depth-only' } : undefined);
    return tex.view;
  }

  function nearestSampler(tex) {
    const key = 'nearest|' + tex.wrapS + '|' + tex.wrapT;
    let s = SAMPLERS.get(key);
    if (!s) {
      const wrap = (w) => (w === C.CLAMP_TO_EDGE || w === 10496 || w === 33069 ? 'clamp-to-edge' : w === C.MIRRORED_REPEAT ? 'mirror-repeat' : 'repeat');
      s = device.createSampler({ addressModeU: wrap(tex.wrapS), addressModeV: wrap(tex.wrapT), magFilter: 'nearest', minFilter: 'nearest', mipmapFilter: 'nearest', lodMaxClamp: 0 });
      SAMPLERS.set(key, s);
    }
    return s;
  }

  let dummyDepth = null;
  function dummyDepthTexture() {
    if (!dummyDepth) {
      dummyDepth = new GLTexture();
      dummyDepth.width = dummyDepth.height = 1;
      dummyDepth.format = 'depth32float';
      dummyDepth.min = dummyDepth.mag = C.NEAREST;
      createGpuTexture(dummyDepth, 1);
      dummyDepth.written = 1;
    }
    return dummyDepth;
  }

  let samplerIdCounter = 0;
  const samplerIds = new WeakMap();
  function SAMPLER_IDS(s) {
    let id = samplerIds.get(s);
    if (id == null) {
      id = ++samplerIdCounter;
      samplerIds.set(s, id);
    }
    return id;
  }

  let dummy = null;
  function dummyTexture() {
    if (!dummy) {
      dummy = new GLTexture();
      dummy.width = dummy.height = 1;
      dummy.format = 'rgba8unorm';
      dummy.min = dummy.mag = C.NEAREST;
      createGpuTexture(dummy, 1);
      device.queue.writeTexture({ texture: dummy.gpu }, new Uint8Array([0, 0, 0, 255]), { bytesPerRow: 4 }, { width: 1, height: 1 });
      dummy.written = 1;
    }
    return dummy;
  }

  // ------------------------------------------------------------------ blits

  // depth copies: a pass that writes frag_depth from the source texture (depth formats cannot be blitted, and
  // only some can be copied)
  const DEPTH_COPY = `
struct U { src0: vec2f, scale: vec2f, dst0: vec2f, pad: vec2f };
@group(0) @binding(0) var<uniform> u: U;
@group(0) @binding(1) var t: texture_depth_2d;
@vertex fn vs(@builtin(vertex_index) i: u32) -> @builtin(position) vec4f {
  let c = vec2f(f32(i & 1u), f32((i >> 1u) & 1u));
  return vec4f(c * 2.0 - 1.0, 0.0, 1.0);
}
@fragment fn fs(@builtin(position) p: vec4f) -> @builtin(frag_depth) f32 {
  let s = u.src0 + (p.xy - u.dst0) * u.scale;
  return textureLoad(t, vec2i(s), 0);
}`;
  let depthCopyModule = null;
  const depthCopyPipelines = new Map();

  function depthCopy(src, dst, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1) {
    if (!depthCopyModule) depthCopyModule = device.createShaderModule({ code: DEPTH_COPY, label: 'depth copy' });
    let pipe = depthCopyPipelines.get(dst.format);
    if (!pipe) {
      pipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module: depthCopyModule, entryPoint: 'vs' },
        fragment: { module: depthCopyModule, entryPoint: 'fs', targets: [] },
        primitive: { topology: 'triangle-strip' },
        depthStencil: { format: dst.format, depthWriteEnabled: true, depthCompare: 'always' },
      });
      depthCopyPipelines.set(dst.format, pipe);
    }
    endPass();
    // texture rows are OpenGL's rows (render targets are stored bottom-up), so the copy maps texels directly
    const w = Math.abs(dx1 - dx0);
    const h = Math.abs(dy1 - dy0);
    const x = Math.max(0, Math.min(dx0, dx1));
    const y = Math.max(0, Math.min(dy0, dy1));
    const vw = Math.min(w, dst.width - x);
    const vh = Math.min(h, dst.height - y);
    if (vw <= 0 || vh <= 0) return;
    const u = new Float32Array([Math.min(sx0, sx1), Math.min(sy0, sy1), Math.abs(sx1 - sx0) / w, Math.abs(sy1 - sy0) / h, x, y, 0, 0]);
    const r = ringWrite(uniformRing, u, 256);
    const group = device.createBindGroup({
      layout: pipe.getBindGroupLayout(0),
      entries: [
        { binding: 0, resource: { buffer: r.buffer, offset: r.offset, size: 32 } },
        { binding: 1, resource: textureView(src) },
      ],
    });
    const att = { view: attachmentView(dst, 0), depthLoadOp: 'load', depthStoreOp: 'store' };
    if (/stencil/.test(dst.format)) {
      att.stencilLoadOp = 'load';
      att.stencilStoreOp = 'store';
    }
    const e = enc().beginRenderPass({ colorAttachments: [], depthStencilAttachment: att });
    e.setPipeline(pipe);
    e.setBindGroup(0, group);
    e.setViewport(x, y, vw, vh, 0, 1);
    e.draw(4);
    e.end();
    src.lastUse = dst.lastUse = serial;
    dst.written |= 1;
  }

  /** Copies a rectangle of src into a level of t in another format by drawing it. */
  function copyByBlit(src, t, level, xoff, yoff, x, y, w, h) {
    const tmp = new GLFramebuffer();
    tmp.color[0] = { tex: t, level };
    const saved = st.drawFb;
    st.drawFb = tmp;
    blit(src, x, y, x + w, y + h, xoff, yoff, xoff + w, yoff + h, C.NEAREST);
    endPass();
    st.drawFb = saved;
    t.written |= 1 << level;
  }

  /** glGenerateMipmap: each level drawn from the one above with linear filtering. */
  function generateMips(t) {
    const levels = mipCount(t.width, t.height);
    if (t.levels < levels) {
      // recreate with the full chain, keeping level 0
      const old = createGpuTexture(t, levels);
      if (old) {
        endPass();
        enc().copyTextureToTexture({ texture: old, mipLevel: 0 }, { texture: t.gpu, mipLevel: 0 }, [t.width, t.height]);
        pendingDestroy.push(old);
      }
    }
    endPass();
    for (let l = 1; l < t.levels; l++) {
      const w = Math.max(1, t.width >> l);
      const h = Math.max(1, t.height >> l);
      const srcView = t.gpu.createView({ baseMipLevel: l - 1, mipLevelCount: 1 });
      blitView(srcView, t.format, attachmentView(t, l), w, h);
    }
    t.written = (1 << t.levels) - 1;
    t.lastUse = serial;
  }

  /** Draws a whole view into a whole target view (used for mipmaps). */
  function blitView(srcView, format, dstView, w, h) {
    if (!blitModule) {
      blitModule = device.createShaderModule({ code: MAIN_BLIT, label: 'blit' });
      blitSampler = device.createSampler({ magFilter: 'linear', minFilter: 'linear' });
      blitSamplerNearest = device.createSampler({ magFilter: 'nearest', minFilter: 'nearest' });
    }
    const key = 'mip|' + format;
    let pipe = blitPipelines.get(key);
    if (!pipe) {
      pipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module: blitModule, entryPoint: 'vs' },
        fragment: { module: blitModule, entryPoint: 'fs', targets: [{ format }] },
        primitive: { topology: 'triangle-strip' },
      });
      blitPipelines.set(key, pipe);
    }
    // flip -1: texture row 0 stays row 0 (the blit shader's NDC y is mirrored by it)
    const u = new Float32Array([-1, -1, 1, 1, 0, 0, 1, 1, -1, 0, 0, 0]);
    const r = ringWrite(uniformRing, u, 256);
    const group = device.createBindGroup({
      layout: pipe.getBindGroupLayout(0),
      entries: [
        { binding: 0, resource: { buffer: r.buffer, offset: r.offset, size: 48 } },
        { binding: 1, resource: srcView },
        { binding: 2, resource: blitSampler },
      ],
    });
    const e = enc().beginRenderPass({ colorAttachments: [{ view: dstView, loadOp: 'clear', clearValue: { r: 0, g: 0, b: 0, a: 0 }, storeOp: 'store' }] });
    e.setPipeline(pipe);
    e.setBindGroup(0, group);
    e.setViewport(0, 0, w, h, 0, 1);
    e.draw(4);
    e.end();
  }

  let blitModule = null;
  const blitPipelines = new Map();
  let blitSampler = null;
  let blitSamplerNearest = null;

  function blit(src, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, filter) {
    if (!src || !src.gpu) return;
    if (!ensurePass()) return;
    const p = pass;
    const format = p.colorFormats[0];
    if (!format) return;
    if (!blitModule) {
      blitModule = device.createShaderModule({ code: MAIN_BLIT, label: 'blit' });
      blitSampler = device.createSampler({ magFilter: 'linear', minFilter: 'linear' });
      blitSamplerNearest = device.createSampler({ magFilter: 'nearest', minFilter: 'nearest' });
    }
    const key = format + '|' + p.depthFormat;
    let pipe = blitPipelines.get(key);
    if (!pipe) {
      pipe = device.createRenderPipeline({
        layout: 'auto',
        vertex: { module: blitModule, entryPoint: 'vs' },
        fragment: { module: blitModule, entryPoint: 'fs', targets: p.colorFormats.map((f, i) => (f ? { format: f, writeMask: i === 0 ? 15 : 0 } : null)) },
        primitive: { topology: 'triangle-strip' },
        depthStencil: p.depthFormat ? { format: p.depthFormat, depthWriteEnabled: false, depthCompare: 'always' } : undefined,
      });
      blitPipelines.set(key, pipe);
    }
    const W = p.width;
    const H = p.height;
    const u = new Float32Array([
      2 * dx0 / W - 1, 2 * dy0 / H - 1, 2 * dx1 / W - 1, 2 * dy1 / H - 1,
      sx0 / src.width, sy0 / src.height, sx1 / src.width, sy1 / src.height,
      p.flip, 0, 0, 0,
    ]);
    const r = ringWrite(uniformRing, u, 256);
    if (!src.view) src.view = src.gpu.createView();
    const group = device.createBindGroup({
      layout: pipe.getBindGroupLayout(0),
      entries: [
        { binding: 0, resource: { buffer: r.buffer, offset: r.offset, size: 48 } },
        { binding: 1, resource: src.view },
        { binding: 2, resource: filter === C.NEAREST ? blitSamplerNearest : blitSampler },
      ],
    });
    src.lastUse = serial;
    p.encoder.setPipeline(pipe);
    p.pipeline = pipe;
    p.encoder.setBindGroup(0, group);
    p.encoder.setViewport(0, 0, W, H, 0, 1);
    p.encoder.setScissorRect(0, 0, W, H);
    p.vp = '';
    p.sc = '';
    p.encoder.draw(4);
  }

  // ------------------------------------------------------------------ the context object

  function createContext() {
    const gl = Object.create(null);
    Object.assign(gl, CONSTANTS);
    gl.canvas = canvas;
    gl.isWebGPU = true;
    gl.stats = stats;
    Object.defineProperty(gl, 'drawingBufferWidth', { get: () => canvas.width });
    Object.defineProperty(gl, 'drawingBufferHeight', { get: () => canvas.height });

    gl.getError = () => 0;
    gl.getExtension = () => null;
    gl.getSupportedExtensions = () => [];
    gl.isContextLost = () => false;
    gl.getContextAttributes = () => ({ alpha: false, depth: true, stencil: true, antialias: false });
    gl.flush = () => {};
    gl.finish = () => flush();

    gl.getParameter = (p) => {
      switch (p) {
        case C.VENDOR: return 'TeaFabric';
        case C.RENDERER: return 'WebGPU' + (adapterInfo ? ' (' + adapterInfo + ')' : '');
        case C.VERSION: return 'WebGL 2.0 (TeaFabric on WebGPU)';
        case C.SHADING_LANGUAGE_VERSION: return 'WebGL GLSL ES 3.00';
        case C.MAX_TEXTURE_SIZE: case C.MAX_RENDERBUFFER_SIZE: return device.limits.maxTextureDimension2D;
        case C.MAX_TEXTURE_IMAGE_UNITS: case C.MAX_COMBINED_TEXTURE_IMAGE_UNITS: return 16;
        case C.MAX_VERTEX_ATTRIBS: return 16;
        case C.MAX_DRAW_BUFFERS: case C.MAX_COLOR_ATTACHMENTS: return 8;
        case C.VIEWPORT: return new Int32Array(st.viewport);
        case C.SCISSOR_BOX: return new Int32Array(st.scissor);
        case C.MAX_VIEWPORT_DIMS: return new Int32Array([8192, 8192]);
        default:
          warnOnce('param' + p, 'getParameter(' + p + ') is not supported');
          return 0;
      }
    };

    // ---- capabilities and fixed state
    const caps = {
      [C.BLEND]: (v) => { st.blend = v; }, [C.DEPTH_TEST]: (v) => { st.depthTest = v; },
      [C.CULL_FACE]: (v) => { st.cull = v; }, [C.SCISSOR_TEST]: (v) => { st.scissorTest = v; },
      [C.POLYGON_OFFSET_FILL]: (v) => { st.offsetFill = v; },
    };
    gl.enable = (c) => { if (caps[c]) caps[c](true); else if (c === C.STENCIL_TEST) warnOnce('stencil', 'stencil test is not supported'); };
    gl.disable = (c) => { if (caps[c]) caps[c](false); };
    gl.isEnabled = (c) => ({ [C.BLEND]: st.blend, [C.DEPTH_TEST]: st.depthTest, [C.CULL_FACE]: st.cull, [C.SCISSOR_TEST]: st.scissorTest, [C.POLYGON_OFFSET_FILL]: st.offsetFill }[c] || false);
    gl.blendFunc = (s, d) => { st.srcRGB = st.srcA = s; st.dstRGB = st.dstA = d; };
    gl.blendFuncSeparate = (sr, dr, sa, da) => { st.srcRGB = sr; st.dstRGB = dr; st.srcA = sa; st.dstA = da; };
    gl.blendEquation = (e) => { st.eqRGB = st.eqA = e; };
    gl.blendEquationSeparate = (e, a) => { st.eqRGB = e; st.eqA = a; };
    gl.blendColor = (r, g, b, a) => { st.blendColor = [r, g, b, a]; };
    gl.depthFunc = (f) => { st.depthFunc = f; };
    gl.depthMask = (m) => { st.depthMask = !!m; };
    gl.colorMask = (r, g, b, a) => { st.colorMask = [!!r, !!g, !!b, !!a]; };
    gl.cullFace = (m) => { st.cullFace = m; };
    gl.frontFace = (m) => { st.frontFace = m; };
    gl.polygonOffset = (f, u) => { st.offsetFactor = f; st.offsetUnits = u; };
    gl.lineWidth = () => {};
    gl.stencilFunc = gl.stencilMask = gl.stencilOp = gl.clearStencil = gl.stencilFuncSeparate = gl.stencilOpSeparate = gl.stencilMaskSeparate = () => {};
    gl.viewport = (x, y, w, h) => { st.viewport = [x | 0, y | 0, w | 0, h | 0]; };
    gl.scissor = (x, y, w, h) => { st.scissor = [x | 0, y | 0, w | 0, h | 0]; };
    gl.clearColor = (r, g, b, a) => { st.clearColor = [r, g, b, a]; };
    gl.clearDepth = (d) => { st.clearDepth = d; };
    gl.hint = () => {};
    gl.pixelStorei = (p, v) => {
      if (p === C.UNPACK_ALIGNMENT) st.unpackAlignment = v;
      else if (p === C.UNPACK_ROW_LENGTH) st.unpackRowLength = v;
      else if (p === C.UNPACK_SKIP_ROWS) st.unpackSkipRows = v;
      else if (p === C.UNPACK_SKIP_PIXELS) st.unpackSkipPixels = v;
    };

    gl.clear = (mask) => {
      const fb = st.drawFb;
      const color = (mask & C.COLOR_BUFFER_BIT) !== 0;
      const depth = (mask & C.DEPTH_BUFFER_BIT) !== 0 && st.depthMask;
      if (!color && !depth) return;
      if (st.scissorTest) {
        const t = fb.isDefault ? { width: canvas.width, height: canvas.height } : targets(fb);
        const s = st.scissor;
        if (t && (s[0] > 0 || s[1] > 0 || s[0] + s[2] < t.width || s[1] + s[3] < t.height)) {
          warnOnce('scissorclear', 'clears inside a scissor rectangle are not supported; clearing everything');
        }
      }
      if (color && !st.colorMask.every(Boolean)) warnOnce('maskclear', 'clears with a partial colour mask are not supported');
      if (pass && pass.fb === fb) endPass();
      if (color) {
        // only the draw buffers are cleared
        for (let i = 0; i < 8; i++) if (drawBufferOn(fb, i)) fb.pendingColors[i] = st.clearColor.slice();
      }
      if (depth) fb.pendingDepth = st.clearDepth;
      fbsWithPendingClears.add(fb);
    };

    // ---- buffers
    gl.createBuffer = () => new GLBuffer();
    gl.deleteBuffer = (b) => { if (b && b.owned && b.gpu) pendingDestroy.push(b.gpu); if (b) b.gpu = null; };
    gl.isBuffer = (b) => b instanceof GLBuffer;
    const bound = (target) => {
      switch (target) {
        case C.ARRAY_BUFFER: return st.arrayBuffer;
        case C.ELEMENT_ARRAY_BUFFER: return st.elementBuffer;
        case C.COPY_READ_BUFFER: return st.copyRead;
        case C.COPY_WRITE_BUFFER: return st.copyWrite;
        default: return null;
      }
    };
    gl.bindBuffer = (target, b) => {
      switch (target) {
        case C.ARRAY_BUFFER: st.arrayBuffer = b; break;
        case C.ELEMENT_ARRAY_BUFFER: st.elementBuffer = b; break;
        case C.COPY_READ_BUFFER: st.copyRead = b; break;
        case C.COPY_WRITE_BUFFER: st.copyWrite = b; break;
        default: break;
      }
    };
    gl.bufferData = (target, data, usage) => {
      const b = bound(target);
      if (!b) return;
      if (b.owned && b.gpu) pendingDestroy.push(b.gpu); // commands recorded earlier keep the old storage
      b.gpu = null;
      b.owned = false;
      b.cpu = null;
      if (typeof data === 'number') {
        b.size = data;
        b.gpu = device.createBuffer({ size: Math.max(4, Math.ceil(data / 4) * 4), usage: GPU_BUFFER.VERTEX | GPU_BUFFER.INDEX | GPU_BUFFER.COPY_DST | GPU_BUFFER.COPY_SRC });
        b.offset = 0;
        b.owned = true;
        return;
      }
      const bytes = data instanceof Uint8Array ? data : new Uint8Array(data.buffer || data, data.byteOffset || 0, data.byteLength);
      b.size = bytes.byteLength;
      if (target === C.ELEMENT_ARRAY_BUFFER) b.cpu = bytes.slice(); // UNSIGNED_BYTE indices are widened at draw time
      if (usage === C.STREAM_DRAW) {
        const r = ringWrite(vertexRing, bytes, 4);
        b.gpu = r.buffer;
        b.offset = r.offset;
        return;
      }
      const size = Math.max(4, Math.ceil(bytes.byteLength / 4) * 4);
      b.gpu = device.createBuffer({ size, usage: GPU_BUFFER.VERTEX | GPU_BUFFER.INDEX | GPU_BUFFER.COPY_DST | GPU_BUFFER.COPY_SRC });
      b.offset = 0;
      b.owned = true;
      if (bytes.byteLength & 3) {
        const padded = new Uint8Array(size);
        padded.set(bytes);
        device.queue.writeBuffer(b.gpu, 0, padded);
      } else {
        device.queue.writeBuffer(b.gpu, 0, bytes.buffer, bytes.byteOffset, bytes.byteLength);
      }
    };
    gl.bufferSubData = (target, offset, data) => {
      const b = bound(target);
      if (!b || !b.gpu) return;
      if (!b.owned) {
        warnOnce('subdata', 'bufferSubData on a streamed buffer is not supported');
        return;
      }
      if (b.lastUse === serial) flush();
      const bytes = data instanceof Uint8Array ? data : new Uint8Array(data.buffer, data.byteOffset, data.byteLength);
      device.queue.writeBuffer(b.gpu, offset, bytes.buffer, bytes.byteOffset, bytes.byteLength & ~3);
    };
    gl.copyBufferSubData = (readTarget, writeTarget, readOffset, writeOffset, size) => {
      const r = bound(readTarget);
      const w = bound(writeTarget);
      if (!r || !w || !r.gpu || !w.gpu) return;
      endPass();
      enc().copyBufferToBuffer(r.gpu, r.offset + readOffset, w.gpu, w.offset + writeOffset, size);
    };

    // ---- vertex attributes
    gl.enableVertexAttribArray = (i) => { st.attribs[i].enabled = true; };
    gl.disableVertexAttribArray = (i) => { st.attribs[i].enabled = false; };
    gl.vertexAttribPointer = (i, size, type, normalized, stride, offset) => {
      const a = st.attribs[i];
      a.buffer = st.arrayBuffer;
      a.size = size;
      a.type = type;
      a.normalized = !!normalized;
      a.stride = stride;
      a.offset = offset;
    };
    gl.vertexAttribIPointer = (i, size, type, stride, offset) => gl.vertexAttribPointer(i, size, type, false, stride, offset);
    gl.vertexAttribDivisor = (i, d) => { st.attribs[i].divisor = d; };
    gl.vertexAttrib4f = (i, x, y, z, w) => { st.generic[i] = new Float32Array([x, y, z, w]); };
    gl.vertexAttrib1f = (i, x) => gl.vertexAttrib4f(i, x, 0, 0, 1);
    gl.vertexAttrib2f = (i, x, y) => gl.vertexAttrib4f(i, x, y, 0, 1);
    gl.vertexAttrib3f = (i, x, y, z) => gl.vertexAttrib4f(i, x, y, z, 1);
    gl.createVertexArray = () => ({});
    gl.bindVertexArray = () => {};
    gl.deleteVertexArray = () => {};

    // ---- textures
    gl.createTexture = () => new GLTexture();
    gl.deleteTexture = (t) => { if (t && t.gpu) pendingDestroy.push(t.gpu); if (t) { t.gpu = null; t.generation++; } };
    gl.isTexture = (t) => t instanceof GLTexture;
    gl.activeTexture = (u) => { st.activeUnit = u - C.TEXTURE0; };
    gl.bindTexture = (target, t) => { if (target === C.TEXTURE_2D) st.units[st.activeUnit] = t; };
    const tex2d = () => st.units[st.activeUnit];
    gl.texParameteri = (target, p, v) => {
      const t = tex2d();
      if (!t) return;
      switch (p) {
        case C.TEXTURE_MIN_FILTER: t.min = v; break;
        case C.TEXTURE_MAG_FILTER: t.mag = v; break;
        case C.TEXTURE_WRAP_S: t.wrapS = v; break;
        case C.TEXTURE_WRAP_T: t.wrapT = v; break;
        case C.TEXTURE_MAX_LEVEL: t.maxLevel = v; break;
        case C.TEXTURE_COMPARE_MODE: t.compareMode = v; break;
        case C.TEXTURE_COMPARE_FUNC: t.compareFunc = v; break;
        default: break;
      }
    };
    gl.texParameterf = (target, p, v) => {
      if (p === C.TEXTURE_MAX_LOD || p === C.TEXTURE_MIN_LOD) return;
      gl.texParameteri(target, p, v);
    };
    gl.texImage2D = function (target, level, internal, width, height, border, format, type, data) {
      const t = tex2d();
      if (!t || target !== C.TEXTURE_2D) return;
      if (arguments.length === 6) { // texImage2D(target, level, internal, format, type, source) with an image
        warnOnce('teximage6', 'texImage2D from an image element is not supported');
        return;
      }
      const fmt = gpuFormat(internal, format, type);
      if (level === 0) {
        if (!t.gpu || t.format !== fmt || t.width !== width || t.height !== height) {
          t.format = fmt;
          t.width = width;
          t.height = height;
          t.written = 0;
          const levels = t.maxLevel < 1000 && !/^depth/.test(fmt) ? Math.min(t.maxLevel + 1, mipCount(width, height)) : 1;
          const old = createGpuTexture(t, levels);
          if (old) pendingDestroy.push(old);
        }
      } else if (t.gpu && level >= t.levels) {
        // a mipmap level beyond the texture's: recreate with enough levels, keeping what was written
        const levels = Math.min(level + 1, mipCount(t.width, t.height));
        const old = createGpuTexture(t, levels);
        if (old) {
          endPass();
          const n = Math.min(old.mipLevelCount, levels);
          for (let l = 0; l < n; l++) {
            if (t.written & (1 << l)) {
              enc().copyTextureToTexture({ texture: old, mipLevel: l }, { texture: t.gpu, mipLevel: l },
                [Math.max(1, t.width >> l), Math.max(1, t.height >> l)]);
            }
          }
          pendingDestroy.push(old);
        }
      }
      if (data) upload(t, level, 0, 0, width, height, format, type, data);
    };
    gl.texSubImage2D = function (target, level, x, y, width, height, format, type, data) {
      const t = tex2d();
      if (!t || !t.gpu || target !== C.TEXTURE_2D) return;
      upload(t, level, x, y, width, height, format, type, data);
    };
    gl.copyTexSubImage2D = (target, level, xoff, yoff, x, y, w, h) => {
      const t = tex2d();
      const fb = st.readFb;
      const src = fb && !fb.isDefault && fb.color[0] ? fb.color[0].tex : null;
      if (t && t.gpu && src && src.gpu && src.format !== t.format && !isDepthFormat(t.format)) {
        // another format: draw it (as a blit into a framebuffer made of the texture level)
        copyByBlit(src, t, level, xoff, yoff, x, y, w, h);
        return;
      }
      if (!t || !t.gpu || !src || !src.gpu || src.format !== t.format) {
        warnOnce('copytex', 'copyTexSubImage2D from the screen is not supported');
        return;
      }
      endPass();
      enc().copyTextureToTexture({ texture: src.gpu, origin: { x, y } }, { texture: t.gpu, mipLevel: level, origin: { x: xoff, y: yoff } }, [w, h]);
      t.written |= 1 << level;
    };
    gl.copyTexImage2D = (target, level, internal, x, y, w, h, border) => {
      const t = tex2d();
      if (!t || target !== C.TEXTURE_2D) return;
      const fb = st.readFb;
      const src = fb && !fb.isDefault && fb.color[0] ? fb.color[0].tex : null;
      const fmt = src ? src.format : gpuFormat(internal, C.RGBA, C.UNSIGNED_BYTE);
      gl.texImage2D(target, level, internal === C.RGBA || internal === C.RGB ? internal : internal, w, h, 0,
        C.RGBA, fmt === 'rgba16float' ? C.HALF_FLOAT : C.UNSIGNED_BYTE, null);
      gl.copyTexSubImage2D(target, level, 0, 0, x, y, w, h);
    };
    gl.generateMipmap = (target) => {
      const t = tex2d();
      if (!t || !t.gpu || target !== C.TEXTURE_2D || isDepthFormat(t.format)) return;
      generateMips(t);
    };

    // ---- framebuffers
    gl.createFramebuffer = () => new GLFramebuffer();
    gl.deleteFramebuffer = (f) => {
      if (st.drawFb === f) st.drawFb = DEFAULT_FB;
      if (st.readFb === f) st.readFb = DEFAULT_FB;
    };
    gl.bindFramebuffer = (target, f) => {
      const fb = f || DEFAULT_FB;
      if (target === C.FRAMEBUFFER || target === C.DRAW_FRAMEBUFFER) st.drawFb = fb;
      if (target === C.FRAMEBUFFER || target === C.READ_FRAMEBUFFER) st.readFb = fb;
    };
    gl.framebufferTexture2D = (target, attachment, textarget, t, level) => {
      const fb = target === C.READ_FRAMEBUFFER ? st.readFb : st.drawFb;
      if (!fb || fb.isDefault) return;
      const a = t ? { tex: t, level: level | 0 } : null;
      if (attachment >= C.COLOR_ATTACHMENT0 && attachment < C.COLOR_ATTACHMENT0 + 8) fb.color[attachment - C.COLOR_ATTACHMENT0] = a;
      else if (attachment === C.DEPTH_ATTACHMENT || attachment === C.DEPTH_STENCIL_ATTACHMENT) fb.depth = a;
    };
    gl.checkFramebufferStatus = () => C.FRAMEBUFFER_COMPLETE;
    gl.getFramebufferAttachmentParameter = (target, attachment, pname) => {
      const fb = target === C.READ_FRAMEBUFFER ? st.readFb : st.drawFb;
      if (pname === 36048) { // FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE
        if (fb.isDefault) return 33304; // FRAMEBUFFER_DEFAULT
        const a = attachment === C.DEPTH_ATTACHMENT ? fb.depth : fb.color[attachment - C.COLOR_ATTACHMENT0];
        return a ? 5890 : 0; // TEXTURE
      }
      if (pname === 36049) { // FRAMEBUFFER_ATTACHMENT_OBJECT_NAME
        const a = attachment === C.DEPTH_ATTACHMENT ? fb.depth : fb.color[attachment - C.COLOR_ATTACHMENT0];
        return a ? a.tex : null;
      }
      return 0;
    };
    gl.drawBuffers = (bufs) => {
      const fb = st.drawFb;
      if (!fb.isDefault) fb.drawBuffers = Array.from(bufs);
    };
    gl.readBuffer = () => {};
    gl.blitFramebuffer = (sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, mask, filter) => {
      if (mask & C.DEPTH_BUFFER_BIT) {
        const src = st.readFb.depth && st.readFb.depth.tex;
        const dst = st.drawFb.depth && st.drawFb.depth.tex;
        if (src && dst && src.gpu && dst.gpu) depthCopy(src, dst, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1);
        else warnOnce('depthblit', 'depth blits to or from the screen are not supported');
      }
      if (!(mask & C.COLOR_BUFFER_BIT)) return;
      const fb = st.readFb;
      const src = fb && !fb.isDefault && fb.color[0] ? fb.color[0].tex : null;
      if (!src) {
        warnOnce('blitscreen', 'blitting from the screen is not supported');
        return;
      }
      blit(src, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, filter);
    };
    gl.readPixels = (x, y, w, h, format, type, out) => {
      warnOnce('readpixels', 'readPixels is not supported (screenshots and texture reads come back black)');
      if (out && out.fill) out.fill(0);
    };

    // ---- shaders and programs
    gl.createShader = (type) => new GLShader(type);
    gl.deleteShader = () => {};
    gl.shaderSource = (s, src) => { s.source = src; };
    gl.compileShader = () => {};
    gl.getShaderParameter = (s, p) => (p === C.COMPILE_STATUS ? true : p === 35663 ? s.type : 0);
    gl.getShaderInfoLog = () => '';
    gl.createProgram = () => new GLProgram();
    gl.deleteProgram = (p) => { if (p) { p.pipelines.clear(); p.variants.clear(); } };
    gl.attachShader = (p, s) => { if (p.shaders.indexOf(s) < 0) p.shaders.push(s); };
    gl.detachShader = (p, s) => { p.shaders = p.shaders.filter((x) => x !== s); };
    gl.bindAttribLocation = (p, loc, name) => { p.bindings[name] = loc; };
    gl.linkProgram = (p) => linkProgram(p);
    gl.getProgramParameter = (p, pname) => {
      if (pname === C.LINK_STATUS) return p.linked;
      if (pname === C.DELETE_STATUS) return false;
      if (pname === C.ACTIVE_UNIFORMS) return p.conv ? p.conv.uniforms.length + p.conv.samplers.length - 1 : 0;
      if (pname === C.ACTIVE_ATTRIBUTES) return p.conv ? p.conv.attributes.length : 0;
      return 0;
    };
    gl.getProgramInfoLog = (p) => p.log;
    const UNIFORM_TYPES = {
      float: 5126, vec2: 35664, vec3: 35665, vec4: 35666, int: 5124, ivec2: 35667, ivec3: 35668, ivec4: 35669,
      uint: 5125, uvec2: 36294, uvec3: 36295, uvec4: 36296, bool: 35670, bvec2: 35671, bvec3: 35672, bvec4: 35673,
      mat2: 35674, mat3: 35675, mat4: 35676, sampler2D: 35678, sampler3D: 35679, samplerCube: 35680,
      sampler2DShadow: 35682, sampler2DArray: 36289, isampler2D: 36298, usampler2D: 36306,
    };
    /** The program's uniforms (from the conversion; the user's, without tf_flipY) then its samplers. */
    gl.getActiveUniform = (p, index) => {
      if (!p || !p.conv) return null;
      const list = p.conv.uniforms.filter((u) => u.name !== 'tf_flipY');
      if (index < list.length) {
        const u = list[index];
        return { name: u.count ? u.name + '[0]' : u.name, size: u.count || 1, type: UNIFORM_TYPES[u.type] || 5126 };
      }
      const sm = p.conv.samplers[index - list.length];
      return sm ? { name: sm.name, size: 1, type: UNIFORM_TYPES[sm.type] || 35678 } : null;
    };
    gl.useProgram = (p) => { st.program = p; };
    gl.validateProgram = () => {};
    gl.getAttribLocation = (p, name) => (p.attribByName && p.attribByName[name] ? p.attribByName[name].location : -1);
    gl.getUniformLocation = (p, name) => {
      if (!p.linked) return null;
      const m = /^(\w+)(?:\[(\d+)\])?$/.exec(name);
      if (!m) return null;
      if (p.samplerByName[m[1]] != null) return { prog: p, sampler: p.samplerByName[m[1]] };
      const u = p.uniformByName[m[1]];
      if (!u || u.name === 'tf_flipY') return null;
      return { prog: p, u, index: m[2] ? +m[2] : 0 };
    };

    // ---- uniform values
    function setFloats(loc, values, comps, offset) {
      if (!loc || !loc.u) return;
      const p = loc.prog;
      const u = loc.u;
      const stride = u.stride || 16;
      const count = u.count ? Math.min(u.count - loc.index, Math.floor(values.length / comps)) : 1;
      for (let e = 0; e < count; e++) {
        const base = (u.offset + (loc.index + e) * (u.count ? stride : 0)) / 4;
        for (let c = 0; c < comps; c++) p.f32[base + c] = values[(offset || 0) + e * comps + c];
      }
      p.dirty = true;
    }
    function setInts(loc, values, comps) {
      if (!loc) return;
      if (loc.sampler != null) {
        loc.prog.samplerUnits[loc.sampler] = values[0] | 0;
        return;
      }
      if (!loc.u) return;
      const p = loc.prog;
      const u = loc.u;
      const isInt = /^(int|ivec|uint|uvec|bool|bvec)/.test(u.type);
      const base = (u.offset + loc.index * (u.count ? u.stride : 0)) / 4;
      for (let c = 0; c < comps; c++) {
        if (isInt) p.i32[base + c] = values[c] | 0;
        else p.f32[base + c] = values[c];
      }
      p.dirty = true;
    }
    function setMatrix(loc, cols, rows, data, transpose) {
      if (!loc || !loc.u) return;
      const p = loc.prog;
      const colStride = rows === 2 ? 2 : 4; // WGSL: mat2 columns are 8 bytes, mat3 and mat4 columns 16
      const base = loc.u.offset / 4;
      for (let c = 0; c < cols; c++) {
        for (let r = 0; r < rows; r++) {
          p.f32[base + c * colStride + r] = transpose ? data[r * cols + c] : data[c * rows + r];
        }
      }
      p.dirty = true;
    }
    gl.uniform1f = (l, x) => setFloats(l, [x], 1);
    gl.uniform2f = (l, x, y) => setFloats(l, [x, y], 2);
    gl.uniform3f = (l, x, y, z) => setFloats(l, [x, y, z], 3);
    gl.uniform4f = (l, x, y, z, w) => setFloats(l, [x, y, z, w], 4);
    gl.uniform1fv = (l, v) => setFloats(l, v, 1);
    gl.uniform2fv = (l, v) => setFloats(l, v, 2);
    gl.uniform3fv = (l, v) => setFloats(l, v, 3);
    gl.uniform4fv = (l, v) => setFloats(l, v, 4);
    gl.uniform1i = (l, x) => setInts(l, [x], 1);
    gl.uniform2i = (l, x, y) => setInts(l, [x, y], 2);
    gl.uniform3i = (l, x, y, z) => setInts(l, [x, y, z], 3);
    gl.uniform4i = (l, x, y, z, w) => setInts(l, [x, y, z, w], 4);
    gl.uniform1iv = (l, v) => setInts(l, v, 1);
    gl.uniform2iv = (l, v) => setInts(l, v, 2);
    gl.uniform3iv = (l, v) => setInts(l, v, 3);
    gl.uniform4iv = (l, v) => setInts(l, v, 4);
    gl.uniformMatrix2fv = (l, t, v) => setMatrix(l, 2, 2, v, t);
    gl.uniformMatrix3fv = (l, t, v) => setMatrix(l, 3, 3, v, t);
    gl.uniformMatrix4fv = (l, t, v) => setMatrix(l, 4, 4, v, t);

    // ---- draws
    gl.drawArrays = (mode, first, count) => {
      if (first) warnOnce('first', 'drawArrays with first > 0 is not supported');
      draw(mode, count, 0, 0, 1);
    };
    gl.drawElements = (mode, count, type, offset) => draw(mode, count, type, offset, 1);
    gl.drawArraysInstanced = (mode, first, count, n) => draw(mode, count, 0, 0, n);
    gl.drawElementsInstanced = (mode, count, type, offset, n) => draw(mode, count, type, offset, n);

    return gl;
  }

  function start() {
    vertexRing = new Ring(16 << 20, GPU_BUFFER.VERTEX | GPU_BUFFER.INDEX | GPU_BUFFER.COPY_DST | GPU_BUFFER.COPY_SRC, 'vertex ring');
    uniformRing = new Ring(4 << 20, GPU_BUFFER.UNIFORM | GPU_BUFFER.COPY_DST, 'uniform ring');
    return createContext();
  }

  globalThis.TFWebGPU = { init, createContext: start, endFrame, stats };
})();
