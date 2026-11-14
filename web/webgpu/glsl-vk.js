// Converts the GLSL ES 3.00 programs the game hands to WebGL (the fixed-function shaders GLEmu generates
// and mod shaders after GlslTranslator) into Vulkan-style GLSL 4.50 that naga can turn into WGSL:
//  - loose uniforms become members of one uniform block (group 0, binding 0) shared by both stages, so a
//    program's uniform values are one buffer; offsets follow WGSL's uniform layout rules;
//  - each sampler becomes a texture plus a sampler (group 1, bindings 2n and 2n+1);
//  - inputs, varyings and outputs get explicit locations;
//  - the vertex stage maps OpenGL's clip depth (-w..w) to WebGPU's (0..w) and can flip y for
//    off-screen targets (tf_flipY), so render targets keep OpenGL's bottom-up row order.
// Works in the page and in Node (sets globalThis.TFGlslVk).
(function () {
  'use strict';

  const SAMPLER_TYPES = {
    sampler2D: ['texture2D', 'sampler', 'sampler2D', '2d', 'float'],
    sampler2DShadow: ['texture2D', 'samplerShadow', 'sampler2DShadow', '2d', 'depth'],
    samplerCube: ['textureCube', 'sampler', 'samplerCube', 'cube', 'float'],
    sampler3D: ['texture3D', 'sampler', 'sampler3D', '3d', 'float'],
    sampler2DArray: ['texture2DArray', 'sampler', 'sampler2DArray', '2d-array', 'float'],
    isampler2D: ['itexture2D', 'sampler', 'isampler2D', '2d', 'sint'],
    usampler2D: ['utexture2D', 'sampler', 'usampler2D', '2d', 'uint'],
  };

  // WGSL uniform address space: [align, size] (same as std140 for these types; arrays use stride 16).
  const LAYOUT = {
    float: [4, 4], int: [4, 4], uint: [4, 4], bool: [4, 4],
    vec2: [8, 8], ivec2: [8, 8], uvec2: [8, 8], bvec2: [8, 8],
    vec3: [16, 12], ivec3: [16, 12], uvec3: [16, 12], bvec3: [16, 12],
    vec4: [16, 16], ivec4: [16, 16], uvec4: [16, 16], bvec4: [16, 16],
    mat2: [8, 16], mat3: [16, 48], mat4: [16, 64],
  };

  function stripComments(src) {
    return src.replace(/\/\*[\s\S]*?\*\//g, ' ').replace(/\/\/[^\n]*/g, '');
  }

  /** Resolves #ifdef / #ifndef / #if (defined, 0, 1) / #else / #elif / #endif; keeps #define for naga. */
  function preprocess(src) {
    const defined = new Set();
    const out = [];
    const stack = []; // {active, taken}
    const active = () => stack.every((f) => f.active);
    const evalIf = (expr) => {
      const e = expr.replace(/defined\s*\(\s*(\w+)\s*\)/g, (m, n) => (defined.has(n) ? '1' : '0'))
        .replace(/defined\s+(\w+)/g, (m, n) => (defined.has(n) ? '1' : '0'))
        .replace(/\b[A-Za-z_]\w*\b/g, '0');
      try {
        return !!Function('"use strict"; return (' + e + ');')();
      } catch (err) {
        return false;
      }
    };
    for (const line of src.split('\n')) {
      const m = /^\s*#\s*(\w+)\s*(.*)$/.exec(line);
      if (!m) {
        if (active()) out.push(line);
        continue;
      }
      const [, dir, rest] = m;
      if (dir === 'ifdef' || dir === 'ifndef') {
        const v = defined.has(rest.trim().split(/\s+/)[0]);
        const on = dir === 'ifdef' ? v : !v;
        stack.push({ active: on, taken: on });
      } else if (dir === 'if') {
        const on = evalIf(rest);
        stack.push({ active: on, taken: on });
      } else if (dir === 'elif') {
        const top = stack[stack.length - 1];
        const on = !top.taken && evalIf(rest);
        top.active = on;
        top.taken = top.taken || on;
      } else if (dir === 'else') {
        const top = stack[stack.length - 1];
        top.active = !top.taken;
        top.taken = true;
      } else if (dir === 'endif') {
        stack.pop();
      } else if (active()) {
        if (dir === 'define') defined.add(rest.trim().split(/[\s(]/)[0]);
        if (dir === 'undef') defined.delete(rest.trim());
        if (dir === 'version' || dir === 'extension') continue;
        out.push(line);
      }
    }
    return out.join('\n');
  }

  /** Splits a translation unit into top-level statements and function bodies. */
  function topLevel(src) {
    const items = []; // {kind: 'decl'|'func'|'pp', text}
    let depth = 0;
    let start = 0;
    let parens = 0;
    for (let i = 0; i < src.length; i++) {
      const c = src[i];
      if (depth === 0 && c === '#' && /(^|\n)\s*$/.test(src.slice(start, i))) {
        const end = src.indexOf('\n', i);
        const stop = end < 0 ? src.length : end;
        const before = src.slice(start, i).trim();
        if (before) items.push({ kind: 'decl', text: before });
        items.push({ kind: 'pp', text: src.slice(i, stop) });
        i = stop;
        start = stop + 1;
        continue;
      }
      if (c === '(') parens++;
      else if (c === ')') parens--;
      else if (c === '{') {
        depth++;
      } else if (c === '}') {
        depth--;
        if (depth === 0) {
          const text = src.slice(start, i + 1).trim();
          // a struct or block declaration ends with ';' after the brace
          const after = /^\s*(\w+\s*)?(\[[^\]]*\]\s*)?;/.exec(src.slice(i + 1));
          if (after) {
            items.push({ kind: 'decl', text: text + src.slice(i + 1, i + 1 + after[0].length).trim() });
            i += after[0].length;
          } else {
            items.push({ kind: 'func', text });
          }
          start = i + 1;
        }
      } else if (c === ';' && depth === 0 && parens === 0) {
        const text = src.slice(start, i + 1).trim();
        if (text !== ';') items.push({ kind: 'decl', text });
        start = i + 1;
      }
    }
    const rest = src.slice(start).trim();
    if (rest) items.push({ kind: 'decl', text: rest });
    return items;
  }

  const DECL = /^((?:layout\s*\([^)]*\)\s*)?)((?:(?:in|out|uniform|attribute|varying|flat|smooth|noperspective|centroid|highp|mediump|lowp|const|invariant)\s+)*)(\w+)\s+([^;]+);$/;

  function parseDecl(text) {
    const m = DECL.exec(text.replace(/\s+/g, ' ').trim());
    if (!m) return null;
    const layout = m[1].trim();
    const quals = m[2].trim().split(/\s+/).filter(Boolean);
    const type = m[3];
    const names = m[4].split(',').map((s) => s.trim()).map((s) => {
      const nm = /^(\w+)\s*(?:\[\s*(\w+)\s*\])?\s*(=.*)?$/.exec(s);
      return nm ? { name: nm[1], array: nm[2] ? nm[2] : null, init: nm[3] || '' } : null;
    });
    if (names.some((n) => !n)) return null;
    let loc = null;
    const lm = /location\s*=\s*(\d+)/.exec(layout);
    if (lm) loc = +lm[1];
    return { layout, quals, type, names, loc };
  }

  function isQual(d, q) {
    return d.quals.indexOf(q) >= 0;
  }

  /** Parses one stage: declarations by role, the rest of the code. */
  function parseStage(src, stage) {
    const text = preprocess(stripComments(src));
    const items = topLevel(text);
    const st = { stage, inputs: [], outputs: [], uniforms: [], samplers: [], defines: [], other: [] };
    for (const it of items) {
      if (it.kind === 'pp') {
        if (/^\s*#\s*define/.test(it.text)) st.defines.push(it.text);
        continue;
      }
      if (it.kind === 'func') {
        st.other.push(it.text);
        continue;
      }
      if (/^precision\s/.test(it.text)) continue;
      const d = parseDecl(it.text);
      if (!d) {
        st.other.push(it.text);
        continue;
      }
      const uniform = isQual(d, 'uniform');
      const input = isQual(d, 'in') || isQual(d, 'attribute') || (stage === 'fragment' && isQual(d, 'varying'));
      const output = isQual(d, 'out') || (stage === 'vertex' && isQual(d, 'varying'));
      const flat = isQual(d, 'flat');
      for (const n of d.names) {
        if (uniform && SAMPLER_TYPES[d.type]) st.samplers.push({ name: n.name, type: d.type });
        else if (uniform) st.uniforms.push({ name: n.name, type: d.type, array: n.array });
        else if (input) st.inputs.push({ name: n.name, type: d.type, loc: d.loc, flat, array: n.array });
        else if (output) st.outputs.push({ name: n.name, type: d.type, loc: d.loc, flat, array: n.array });
        else st.other.push(it.text.replace(/\s+/g, ' ')); // const globals etc.
      }
    }
    // keep each 'other' declaration once (a declarator list was pushed per name)
    st.other = st.other.filter((t, i, a) => a.indexOf(t) === i);
    return st;
  }

  function arrayLength(n, defines) {
    if (n == null) return 0;
    if (/^\d+$/.test(n)) return +n;
    for (const d of defines) {
      const m = new RegExp('#\\s*define\\s+' + n + '\\s+(\\d+)').exec(d);
      if (m) return +m[1];
    }
    return 1;
  }

  /** Lays out the shared uniform block. */
  function layoutUniforms(list, defines) {
    let offset = 0;
    const out = [];
    for (const u of list) {
      const l = LAYOUT[u.type];
      if (!l) throw new Error('unsupported uniform type ' + u.type + ' ' + u.name);
      const count = arrayLength(u.array, defines);
      let align = l[0];
      let size = l[1];
      let stride = 0;
      if (u.array != null) {
        align = Math.max(16, align); // uniform arrays: element stride rounded up to 16
        stride = Math.ceil(l[1] / 16) * 16;
        size = stride * count;
      }
      offset = Math.ceil(offset / align) * align;
      out.push({ name: u.name, type: u.type, offset, size, count: u.array != null ? count : 0, stride });
      offset += size;
    }
    return { uniforms: out, size: Math.max(16, Math.ceil(offset / 16) * 16) };
  }

  function replaceIdent(code, name, by) {
    return code.replace(new RegExp('\\b' + name + '\\b', 'g'), by);
  }

  /**
   * Converts a program. attribLocations: name -> location from glBindAttribLocation (others are assigned
   * after the highest one). Returns the two Vulkan GLSL sources and the reflection the WebGPU layer needs.
   */
  function convertProgram(vsSrc, fsSrc, attribLocations) {
    const vs = parseStage(vsSrc, 'vertex');
    const fs = parseStage(fsSrc, 'fragment');
    const defines = vs.defines.concat(fs.defines);

    // attributes
    const attribs = [];
    const used = new Set();
    for (const a of vs.inputs) {
      let loc = a.loc != null ? a.loc : attribLocations && attribLocations[a.name] != null ? attribLocations[a.name] : null;
      if (loc != null) used.add(loc);
      attribs.push({ name: a.name, type: a.type, location: loc });
    }
    let next = 0;
    for (const a of attribs) {
      if (a.location == null) {
        while (used.has(next)) next++;
        a.location = next;
        used.add(next);
      }
    }

    // uniforms shared by both stages, in first-seen order; tf_flipY added for the vertex stage
    const all = [];
    const seen = {};
    for (const u of vs.uniforms.concat(fs.uniforms, [{ name: 'tf_flipY', type: 'float', array: null }])) {
      if (!seen[u.name]) {
        seen[u.name] = true;
        all.push(u);
      }
    }
    const block = layoutUniforms(all, defines);
    const blockDecl = 'layout(set = 0, binding = 0) uniform TfUniforms {\n' + all.map((u) => '  ' + u.type + ' ' + u.name
      + (u.array != null ? '[' + u.array + ']' : '') + ';').join('\n') + '\n};\n';

    // samplers: one texture/sampler binding pair per name across both stages
    const samplers = [];
    const sidx = {};
    for (const s of vs.samplers.concat(fs.samplers)) {
      if (sidx[s.name] == null) {
        sidx[s.name] = samplers.length;
        const t = SAMPLER_TYPES[s.type];
        samplers.push({ name: s.name, type: s.type, binding: samplers.length * 2, viewDimension: t[3], sampleType: t[4] });
      }
    }
    const samplerDecls = (stage) => stage.samplers.map((s) => {
      const t = SAMPLER_TYPES[s.type];
      const b = samplers[sidx[s.name]].binding;
      return 'layout(set = 1, binding = ' + b + ') uniform ' + t[0] + ' ' + s.name + '_tfT;\n'
        + 'layout(set = 1, binding = ' + (b + 1) + ') uniform ' + t[1] + ' ' + s.name + '_tfS;\n';
    }).join('');
    const samplerUses = (code, stage) => {
      for (const s of stage.samplers) {
        const t = SAMPLER_TYPES[s.type];
        code = replaceIdent(code, s.name, t[2] + '(' + s.name + '_tfT, ' + s.name + '_tfS)');
      }
      return code;
    };

    // varyings: vertex outputs in order; fragment inputs by name
    const varyings = {};
    vs.outputs.forEach((o, i) => { varyings[o.name] = o.loc != null ? o.loc : i; });
    const vary = (o, dir) => 'layout(location = ' + (varyings[o.name] != null ? varyings[o.name] : 0) + ') '
      + (o.flat ? 'flat ' : '') + dir + ' ' + o.type + ' ' + o.name + (o.array != null ? '[' + o.array + ']' : '') + ';\n';

    const head = '#version 450\n' + defines.join('\n') + '\n' + blockDecl;
    let vcode = vs.other.join('\n');
    vcode = samplerUses(vcode.replace(/\bvoid\s+main\s*\(\s*(void)?\s*\)/, 'void tf_userMain()'), vs);
    const vout = head
      + attribs.map((a) => 'layout(location = ' + a.location + ') in ' + a.type + ' ' + a.name + ';\n').join('')
      + vs.outputs.map((o) => vary(o, 'out')).join('')
      + samplerDecls(vs) + vcode
      + '\nvoid main() {\n  tf_userMain();\n  gl_Position.y *= tf_flipY;\n'
      + '  gl_Position.z = (gl_Position.z + gl_Position.w) * 0.5;\n}\n';

    let fcode = samplerUses(fs.other.join('\n'), fs);
    let outLoc = 0;
    const fout = head
      + fs.inputs.map((o) => vary(o, 'in')).join('')
      + fs.outputs.map((o) => 'layout(location = ' + (o.loc != null ? o.loc : outLoc++) + ') out ' + o.type + ' '
        + o.name + ';\n').join('')
      + samplerDecls(fs) + fcode + '\n';

    return {
      vs: vout,
      fs: fout,
      attributes: attribs,
      uniforms: block.uniforms,
      uniformSize: block.size,
      samplers,
      fragmentOutputs: fs.outputs.length,
    };
  }

  globalThis.TFGlslVk = { convertProgram, preprocess };
})();
