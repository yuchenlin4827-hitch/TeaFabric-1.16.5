#!/usr/bin/env python3
"""Splits a built page into a release build and a debug build.

    python3 scripts/split-debug.py [build/web]

TeaVM writes teavm/classes.wasm with a "name" section (the Java names of every function, local, global, type
and field: about 45 % of the file) and teavm/classes.wasm.teadbg, which the runtime downloads at start-up to
turn stack traces back into Java. Players need neither. Afterwards:

  teavm/classes.wasm         the same module without its name section (the page loads it by default)
  teavm/debug/classes.wasm   the module as compiled, with classes.wasm.teadbg and the deobfuscator next to it
                             (the page loads these with ?debug)

The code is the same in both, only the names differ. scripts/build-web.sh runs this after packaging; running it
again on a split build does nothing.
"""
import os
import shutil
import sys


def leb(b, i):
    result = shift = 0
    while True:
        x = b[i]
        i += 1
        result |= (x & 0x7f) << shift
        shift += 7
        if x < 0x80:
            return result, i


def sections(b):
    """(id, name or None, start, end) of every section; start/end include the section header."""
    if b[:4] != b'\0asm':
        raise ValueError('not a WebAssembly module')
    i = 8
    while i < len(b):
        start = i
        sid = b[i]
        size, i = leb(b, i + 1)
        name = None
        if sid == 0:
            n, j = leb(b, i)
            name = b[j:j + n].decode('utf-8', 'replace')
        yield sid, name, start, i + size
        i += size


def without_names(b):
    parts = [b[:8]]
    for sid, name, start, end in sections(b):
        if not (sid == 0 and name == 'name'):
            parts.append(b[start:end])
    return b''.join(parts)


def main():
    web = sys.argv[1] if len(sys.argv) > 1 else 'build/web'
    teavm = os.path.join(web, 'teavm')
    debug = os.path.join(teavm, 'debug')
    wasm = os.path.join(teavm, 'classes.wasm')
    with open(wasm, 'rb') as f:
        data = f.read()
    if not any(sid == 0 and name == 'name' for sid, name, _, _ in sections(data)):
        if os.path.exists(os.path.join(debug, 'classes.wasm')):
            print('split-debug: already split')
            return
        sys.exit('split-debug: teavm/classes.wasm has no name section and there is no debug build next to it')
    os.makedirs(debug, exist_ok=True)
    shutil.move(wasm, os.path.join(debug, 'classes.wasm'))
    for extra in ('classes.wasm.teadbg', 'classes.wasm-deobfuscator.wasm'):
        src = os.path.join(teavm, extra)
        if os.path.exists(src):
            shutil.move(src, os.path.join(debug, extra))
    release = without_names(data)
    with open(wasm, 'wb') as f:
        f.write(release)
    mb = 1024 * 1024
    print(f'split-debug: release teavm/classes.wasm {len(release) / mb:.1f} MB; '
          f'debug teavm/debug/classes.wasm {len(data) / mb:.1f} MB (+ .teadbg)')


if __name__ == '__main__':
    main()
