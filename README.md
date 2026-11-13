# TeaFabric-1.16.5

Minecraft Java Edition 1.16.5 + Fabric, compiled ahead of time to **WebAssembly GC** with
**TeaVM 0.16** and run in a browser page.

> **概要（中文）**：本專案在建置時從 Mojang 官方伺服器下載 1.16.5 客戶端與資源、從 FabricMC 下載最新穩定版
> Fabric Loader（目前 0.20.1）與 1.16 最新的 Fabric API（0.42.0+1.16，45 個模組），以「真正的」Fabric Loader
> 在建置期套用所有 Mixin，再用 TeaVM 編譯成單一 Wasm GC 模組，並以 WebGL 2 / WebAudio / DOM 取代 LWJGL。
> 「JIT」指的是固定管線 OpenGL 的著色器即時特化：遊戲每使用一種新的 GL 狀態組合，就即時產生並編譯一支專用的
> GLSL ES 3.00 程式並快取。目前狀態：在 Chromium 153 / Chrome 157 中可完整啟動到 **Minecraft 標題畫面**
> （全景背景、Logo、閃爍標語與按鈕，滑鼠可操作）。單人遊戲、存檔與音效尚未完成。詳見下方「Status」。

## Legal

Minecraft is proprietary software owned by Mojang/Microsoft. This repository contains **no**
Minecraft code or assets. Everything Mojang-derived (client jar, libraries, assets, the baked jar,
the compiled `classes.wasm`, `build/web/data/*.zip`) is downloaded or produced locally into
git-ignored directories (`.cache/`, `build/`). Use the result only with your own copy of the game
and do not publish the built web bundle.

## How it works

```
 Mojang (piston-meta / libraries / resources)      FabricMC (meta + maven)
        │  :tools:setupMinecraft (SHA-1 verified)         │  latest stable loader, intermediary,
        ▼                                                  ▼  Fabric API modules from the POM
   .cache/minecraft/…                              .cache/fabric/…
        │                                                  │
        └──────────────► bake (real Fabric Loader / Knot) ◄┘  + baker mod + web mods + ./mods/*.jar
                          • intermediary remap, access wideners, entrypoint patches
                          • Mixin applied to every class, accessors/invokers, synthetic classes
                          • AOT tables: teafabric.generated.Entrypoints, mods.json
                                   │
                                   ▼  .cache/baked/baked.jar  (post-mixin bytecode)
   :platform (browser runtime)     │        generateTables / stubWeb (AutoStub)
   LWJGL→WebGL2/DOM/WebAudio,      │        TypeToken generic supertypes, throwing stubs
   JDK additions, Fabric runtime ──┤        for APIs a browser cannot have (sockets, SSL…)
                                   ▼
                    compileWeb: TeaVM 0.16 → Wasm GC (+ teafabric compiler plugin)
                                   │
                                   ▼  packageWeb → build/web (index.html, boot.js, classes.wasm, data/*.zip)
```

### Build-time Fabric

`bake` launches the official Fabric Loader (`KnotClient`) on the vanilla client with a small mod
(`baker/`) whose `preLaunch` entrypoint pushes every class of every mod through Knot's transformer
chain and Mixin, writes the results to a jar and exits before the game starts. The browser therefore
needs no class loader, ASM or Mixin. Entrypoints are instantiated through a generated class instead
of reflection (method-reference entrypoints get generated proxies), and `FabricLoader` APIs are
served from baked metadata (`platform/…/teafabric/web/fabric`).

Put additional Fabric mods (release jars for 1.16.5) into `./mods/`; they are baked in.

### Browser platform (`platform/`)

| Area | Implementation |
| --- | --- |
| OpenGL 1.1–3.0 (LWJGL `GL11`…`GL30`, ARB/EXT) | `teafabric.web.gl.GLEmu` on WebGL 2: matrix stacks, client arrays streamed from Wasm memory, `GL_QUADS` via a shared index buffer, FBOs, GLSL 1.10 → ES 3.00 translation |
| Fixed-function "JIT" | `ShaderJit`: each new combination of texture environments (incl. `GL_COMBINE`), alpha test, fog, lighting and texgen is turned into a specialised GLSL program on first use and cached |
| GLFW | canvas + DOM events queued in `boot.js`, delivered in `glfwPollEvents`; `glfwSwapBuffers` suspends the game fiber until the next animation frame; pointer lock, clipboard, fullscreen, file drop |
| LWJGL memory (`MemoryUtil`, `MemoryStack`, pointers) | TeaVM's Wasm linear-memory heap; addresses are real offsets that WebGL/WebAudio read through typed-array views |
| OpenAL | WebAudio (buffers, sources, streaming queues, listener, linear distance) |
| STB image/write/resize | pure-Java PNG decoder/encoder, box resize |
| Log4j, OSHI, text2speech | small replacements (console logging, Web Speech narrator) |
| JDK gaps in TeaVM | `java.util.concurrent` (executors, ForkJoin, `CompletableFuture`, locks, queues), `MessageDigest`, `FileChannel`, `UUID`, `java.net` addresses, … |
| Netty | real buffer/local transport; NIO/epoll entry points replaced (no selectors in a page) |
| Online services | offline-mode session service (no JCA/RSA, no CORS access to Mojang) |

### Compiler (`compiler/`)

* `teafabric.compiler.Compile` – TeaVM driver (strict mode, deobfuscation info, problem reports).
* `RedirectTransformer` – call-site and constructor redirects from `META-INF/teafabric/redirects.txt`
  (missing JDK members, `Class.getResource`, `System.exit`, DFU optimisation, …) and JNI natives →
  `UnsatisfiedLinkError`.
* `TeaFabricReflection` – reflection metadata policy (Minecraft/Fabric classes findable by name,
  constructors of Minecraft's nested classes), which Fabric API's reflective helpers need.
* Patched copies of four TeaVM 0.16.0 classes (marked `TeaFabric patch`): nullable block types around
  conditionals and coroutine splits (TeaVM produced invalid modules for Minecraft), JS-value marker
  constructors, and fiber stack growth without `Arrays.copyOf` (infinite recursion while unwinding).
* Function splitting, because V8's optimising tier runs out of zone memory ("V8 process OOM (Zone)")
  on huge Wasm functions and kills the renderer:
  * `tools/SplitClinits` (task `splitClinits`) cuts huge static initialisers such as `Blocks.<clinit>`
    into chains of static methods on bytecode, where the operand stack is empty, no jump/try range
    crosses and no local is live;
  * `teafabric.compiler.wasm.FunctionSplitter` (hooked into a patched `WasmGCTarget`) does the same on
    the Wasm level for TeaVM's generated `teavm@initializer` / `teavm@initReflection` (4 MB and 3 MB).
* `tools/AutoStub` turns TeaVM's missing-symbol report into bytecode stubs that throw
  `UnsupportedOperationException("… is not available in the browser")`, mirroring the real JDK
  hierarchy (so `catch (IOException)` still works). Stubs are lowest priority on the class path.

## Building

Requirements: JDK 21, Gradle 8.14 (or the wrapper), ~6 GB RAM for TeaVM, network access to
piston-meta/piston-data/libraries/resources (Mojang), meta/maven.fabricmc.net and Maven Central.

One command does everything below (download on the first run, bake, compile with the stub loop,
package):

```bash
scripts/build-web.sh                          # game + Fabric API + every jar in ./mods
python3 -m http.server -d build/web 8080      # open http://localhost:8080/
```

The individual steps:

```bash
gradle :tools:setupMinecraft          # download Minecraft 1.16.5 + latest Fabric (once)
gradle :tools:remapIntermediary       # intermediary client (compile classpath for web mods)
gradle bake -PwithFabricApi=true      # Fabric Loader + Fabric API, mixins applied at build time
gradle :platform:classes stubWeb compileWeb   # repeat stubWeb/compileWeb until no missing symbols
                                              # (compileWeb runs generateTables and splitClinits)
gradle packageWeb
python3 -m http.server -d build/web 8080      # open http://localhost:8080/
```

Useful properties: `-PteavmFast=true` (fast dependency analysis), `-PteavmOpt=SIMPLE|ADVANCED|FULL`,
`-PteavmObfuscated=false` (function names in stack traces), `-PteavmStrict=false`,
`-PteavmDeobfuscation=false`, `-PteavmHeap=8g` (compiler JVM heap), `-PteavmMaxHeap=1024` (Wasm heap, MiB),
`-PteavmTarget=js` (TeaVM JavaScript backend instead of Wasm GC), `-PwebLanguages=all` or `=zh_tw,ja_jp`.

## Multiplayer

A web page cannot open TCP connections, so the game connects to servers through a small
WebSocket-to-TCP relay. Run it next to (or in front of) the Minecraft server:

```bash
node proxy/ws-proxy.js --target mc.example.com:25565            # ws://<this host>:8081/
node proxy/ws-proxy.js --target localhost:25565 --listen 443 --cert cert.pem --key key.pem   # wss://
```

In the game, enter the relay's address as the server address (Add Server or Direct Connection), e.g.
`wss://proxy.example.com/` or `ws://192.168.1.10:8081`. A relay started with `--allow host1,host2`
(or `--allow-any`, an open relay) lets the address choose the server:
`wss://proxy.example.com/?target=mc.example.com:25565`. A page opened as `index.html?proxy=wss://…`
accepts plain `host:port` addresses and sends them through that relay; `index.html?server=wss://…`
connects right after start-up. Pages served over `https://` can only use `wss://`.

The relay has no dependencies (Node.js 16+); `node proxy/ws-proxy.js --help` lists the options
(listen address, allow-list, TLS, allowed page origins). Only offline-mode servers can be joined
(`online-mode=false`, or a proxy such as Velocity/Bungee handling authentication): a browser cannot
log in to Microsoft accounts or reach Mojang's session servers.

## Mods

Fabric mods (jars for Minecraft 1.16.5) go into `./mods`; run `scripts/build-web.sh` again. Mods
are baked in at build time: Fabric Loader and Mixin run during `gradle bake`, and TeaVM compiles the
game and the mods together into one WebAssembly module. Nested jars (jar-in-jar), multi-release jars
and classes that MixinExtras generates while applying mixins are all baked in; mod resources stay
reachable through the class path (`getResourceAsStream`) as on a JVM.

**Loading a mod jar into a running page is not possible**, and no amount of patching changes that:

* there is no JVM, class loader or bytecode in the page, only Wasm compiled ahead of time from the
  whole program (dead code removed, calls devirtualised and inlined across Minecraft and all mods);
* a Fabric mod is mostly mixins, which rewrite *Minecraft's own* methods; those methods are already
  machine code, compiled with the mixins of the mods present at build time;
* the alternative, a JVM interpreter written for Wasm running the whole game, would be an entirely
  different project and orders of magnitude slower.

What does work without a rebuild:

* **Resource packs**: drag the pack `.zip` onto the page while the Resource Packs screen is open.
* **Data packs**: drag them onto Create World → Data Packs (singleplayer), or install them on a server.
* **Server-side mods and plugins** of the server you connect to (anything that needs no client code).

Rebuilding after adding jars to `./mods` takes about 15–25 minutes (bake + TeaVM + packaging), and
the result is a new static `build/web` directory to serve.

### Server protocol: 1.12.2 / 1.16.5 (ViaFabric)

With [ViaFabric](https://modrinth.com/mod/viafabric) 0.4.x (it includes ViaVersion) in `./mods`, the
1.16.5 client can join servers running older versions (1.8 – 1.16.4) — ViaVersion translates in the
newer-client-to-older-server direction, so no ViaBackwards is needed. Pick the version with the **VIA**
button at the top right of the Multiplayer screen: press *Enable client-side* and type the version
(`1.12.2`, or a protocol number such as `340`) in the field. Or open the page with `?protocol=1.12.2`
(also `?protocol=auto` or a protocol number); `?protocol=1.16.5` connects natively. The in-game setting
lives in the in-memory game directory, so it lasts until the page is reloaded; the URL switch is applied
at every start. Tested: joining a vanilla 1.12.2 server through the relay, world, movement and chat; F3
shows `[ViaFabric] … C: 1.16.4-1.16.5 (754) S: 1.12.2 (340)`.

ViaVersion needed a few browser accommodations (all in the platform layer, inactive without the mod):
its JvmDowngrader stubs and `PipelineUtil` reflect on JDK / Netty internals and are redirected to
direct implementations, its relocated Gson `TypeToken`s get build-time generic-supertype tables, and its
update check is skipped. Its protocol auto-detection (`AUTO`) cannot open a second connection from the
page, so pick the version explicitly.

Page switches (debugging): `?log` (log overlay), `?username=Name`, `?threads` (Minecraft worker pools
on green threads instead of inline), `?trace` (GL upload/draw trace), `?beacon` (log lines are sent to
the HTTP server, survives renderer crashes), `?nogl`, `?noaudio`, `?nobuiltins`, `?nodeobf`,
`?nofinalize` / `?noweak` (no GC callbacks into Wasm / strong weak references), `?waits` (print the
Java stack of any lock/future/park wait blocked for more than 5 s: finds green-thread deadlocks),
`?vanillaMain` (start through `net.minecraft.client.main.Main`), `?debug` (DEBUG log level),
`?netdebug` (stacks for disconnects and closed connections).

## Status

Working:

* Download pipeline (SHA-1 verified), latest Fabric Loader (0.20.1) and Fabric API (0.42.0+1.16).
* Build-time Mixin application for all 50 mods; Fabric API client entrypoints run in the browser.
* TeaVM 0.16 compiles Minecraft + Fabric (≈20,000 classes, ≈120,000 methods) into a valid Wasm GC module.
* In Chromium 153 and Chrome 157: `Bootstrap` (all registries) in ≈2.5 s, `MinecraftClient`
  construction with the complete resource reload (all texture atlases, models, sound engine) in ≈15 s,
  the Mojang loading screen, and the **title screen** (panorama, logo, splash text, buttons, mouse
  hover) rendered through WebGL 2 and the fixed-function shader JIT, frame-paced by
  `requestAnimationFrame`.
* Text input in screens (letters, IME / Chinese, clipboard), multiplayer over `ws://` / `wss://`
  (joining a 1.16.5 server, world rendering, walking, chat, the F3 screen).
* TeaVM class-library bugs found by running the same programs on the JVM and in the browser and
  diffing the output, all patched in `compiler/src/runtime`: `ArrayDeque` iterator removal,
  `ListIterator.add`, `java.util.Random` (now bit-identical to the JDK), `Math.round/rint/ulp/cbrt/log10`,
  `String.format` (`%%`, `%n`, `%f` rounding and `-0.0`), superclass-first static initialisation (in the
  `SplitClinits` pre-pass), `ConcurrentHashMap.newKeySet`, `Logger(String, String)`.

Not working yet / known issues:

* Fixed on the way to the title screen (TeaVM 0.16.0 issues, all patched in `compiler/`):
  `@Async` implementations were coroutine-transformed when anything they call may suspend
  (`AsyncMethodFinder`; `Object.wait` never released its monitor, `requestAnimationFrame` was never
  called), contended monitors dropped all but one queued thread (`TObject.waitForOtherThreads`),
  interrupted waits resumed without the monitor, and `assert` statements were enabled
  (`desiredAssertionStatus`); ICU4J's property data is now packaged for Minecraft's Bidi text code.
* Fixed: the earlier Chromium renderer crash was V8's optimising compiler running out of zone memory
  (`V8 process OOM (Zone)`, Turboshaft `WasmGCOptimize` phase) when tiering up the 230 KB Wasm
  function of `Blocks.<clinit>`; found with a gdb-attached renderer, a CDP trace and
  `--wasm-tier-up-filter`, fixed by the function splitters above. Strict mode stays enabled because
  null dereferences relying on engine traps crash Chromium (reproduced with a 10-line TeaVM program).
* Sounds: Ogg Vorbis decoding is not wired up yet (`.ogg` assets are not packaged).
* No world persistence (IndexedDB) yet; integrated server / single-player untested.
* Multiplayer needs a WebSocket ↔ TCP proxy (browsers cannot open TCP sockets); online-mode
  authentication is not available.
* Performance work (optimisation level, uniform caching, streaming buffers) has not started.
