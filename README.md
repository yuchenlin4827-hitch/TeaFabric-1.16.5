# TeaFabric-1.16.5

Minecraft Java Edition 1.16.5 + Fabric, compiled ahead of time to **WebAssembly GC** with
**TeaVM 0.16** and run in a browser page.

> **概要（中文）**：本專案在建置時從 Mojang 官方伺服器下載 1.16.5 客戶端與資源、從 FabricMC 下載最新穩定版
> Fabric Loader（目前 0.20.1）與 1.16 最新的 Fabric API（0.42.0+1.16，45 個模組），以「真正的」Fabric Loader
> 在建置期套用所有 Mixin，再用 TeaVM 編譯成單一 Wasm GC 模組，並以 WebGL 2 / WebAudio / DOM 取代 LWJGL。
> 「JIT」指的是固定管線 OpenGL 的著色器即時特化：遊戲每使用一種新的 GL 狀態組合，就即時產生並編譯一支專用的
> GLSL ES 3.00 程式並快取。目前狀態：在 Chromium 153 / Chrome 157 中可完整啟動到 **Minecraft 標題畫面**
> （全景背景、Logo、閃爍標語與按鈕，滑鼠可操作），並可玩單人遊戲（世界存在瀏覽器裡）與多人連線，有音效與背景音樂，可切換語言（含繁體/簡體中文）。
> 詳見下方「Status」。

## 線上體驗 / Try it

| | |
|---|---|
| **網頁版**：在瀏覽器裡直接玩（第一次約下載 40 MB，之後只下載更新的部分） | https://teafabric.teafabric-871743.workers.dev/ |
| **下載版**：Windows / macOS / Linux，下載一次（約 133 MB，可續傳），之後在本機開啟 | https://teafabric.teafabric-871743.workers.dev/download/ |
| **QQ 群** | 1063719613 |
| **Discord** | https://discord.gg/GGN6TUSdNN |

- 網站有**密碼保護**：頁面包含 Minecraft 的程式和素材，不能公開發布。密碼請在 QQ 群或 Discord 索取（使用者名稱隨便填）。
- 瀏覽器需要支援 WebAssembly GC：Chrome / Edge 119+、Firefox 120+、Safari 18.2+，建議用電腦（鍵盤加滑鼠）。
- 網頁版第一次載入約 150 MB；網路慢的話建議用下載版。
- 進入前要輸入自己的玩家名稱（不能用 `Player`）。「多人遊戲」清單裡已經有我們的伺服器；第一次進入伺服器時輸入
  `/register 密碼 密碼`，之後每次輸入 `/login 密碼`。

The demo above is password-protected because the page contains Minecraft's code and assets; ask for
access on [Discord](https://discord.gg/GGN6TUSdNN) or in QQ group 1063719613. It needs a browser with
WebAssembly GC (Chrome/Edge 119+, Firefox 120+, Safari 18.2+). See
[Hosting on Cloudflare](#hosting-on-cloudflare-password-protected) to run your own.

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
| stb_vorbis (Minecraft's `OggAudioStream`) | replaced by `net.minecraft.class_4228` in the platform layer: the browser decodes (`decodeAudioData`); `.ogg` files are fetched from `sounds/<hash>.ogg` when first played |
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
`-PteavmTarget=js` (TeaVM JavaScript backend instead of Wasm GC), `-PwebLanguages=all` or `=zh_tw,ja_jp`
(put those translations into `assets.zip` instead of fetching them on demand).

**Sounds and languages** are not in `assets.zip` (253 MB of `.ogg`, 47 MB of translations): each such
object there is a placeholder naming its hash, so Minecraft registers every sound and lists every
language, and the page fetches the real file the first time it is needed, from `sounds/<hash>.ogg` or
`lang/<hash>.json` next to the page (`packageWeb` puts them there; the music needs
`gradle :tools:setupMinecraft -Passets=all`). Choosing a language reloads only the translations, not
every resource as vanilla does, which takes seconds in a page. The title screen has no Realms button,
since Realms needs Mojang's servers and a Microsoft account.

### Hosting on Cloudflare (password-protected)

`scripts/deploy-cloudflare.sh` uploads `build/web` to a Cloudflare Worker (free plan is enough) that only
serves it to people who know the password, since the page contains Minecraft's code and assets and must
not be public. Log in once with `npx wrangler login` (`--device` in containers) or set
`CLOUDFLARE_API_TOKEN`, then:

```bash
scripts/deploy-cloudflare.sh                 # prints a generated password on the first run
PASSWORD=... scripts/deploy-cloudflare.sh    # change the password
WORKER_NAME=my-mc scripts/deploy-cloudflare.sh
```

The browser asks for the password once (any user name); a signed cookie then keeps you in for 30 days.
Cloudflare does not compress these files, so the deploy stores the large ones gzip-compressed under
content-hashed names in `c/` (in parts below the 25 MiB file limit), listed in `files.json`; `boot.js`
downloads them with progress and decompresses them natively (`DecompressionStream`). A first visit
downloads about 40 MB instead of about 147 MB, and the Worker lets browsers keep `c/`, `sounds/` and
`lang/` for a year, so later visits download only what a new build changed (tested: a second visit
fetched none of the large files again). The same Worker relays multiplayer
connections: use `wss://<worker>.workers.dev/relay?target=mc.example.com:25565` as the server address
(not tested yet; the free plan's CPU limits may cut busy connections).

**Downloadable version**: `/download/` (behind the same password) offers the game as a zip for people
who would rather not load 150 MB from the site every time, e.g. on slow connections. It holds `build/web`
plus a small launcher (`launcher/`, Go) for Windows, macOS and Linux that serves it on
`http://127.0.0.1:47265/` and opens the default browser; the port is fixed because the browser keeps
worlds per address. `scripts/package-desktop.sh` builds it (`build/TeaFabric-1.16.5.zip`, about 140 MB with
the sound effects and every language; `SOUNDS=all` adds the music, `SOUNDS=none` leaves sounds out);
the deploy script uploads it in 24 MiB parts that the Worker joins into one download with Range
support, so downloads can be resumed. `SERVER_ADDRESS=wss://…` puts the server address on the page and
in the zip's usage guide; `LAUNCHER=0` leaves the download out. A relay started with `--origin` must
also allow `http://127.0.0.1:47265` for the downloaded version to join.

### A server for the web client on Railway

A 1.16.5 server that the page can join, on [Railway](https://railway.com) (Hobby plan): Paper 1.16.5 from
the `itzg/minecraft-server` image with a volume for the world, plus `proxy/ws-proxy.js` as a second service
(`deploy/railway/relay`) that is the only way in: it reaches the server over Railway's private network
(the server has no public port) and accepts WebSocket connections from your page's origin only.

The server runs in offline mode (the page has no Microsoft account), so names are not checked by Mojang.
[AuthMe Reloaded](https://modrinth.com/plugin/authmereloaded) protects them instead: the first time a
name joins, it must `/register <password> <password>`; after that, every join needs `/login <password>`
before the player can move, chat or use commands. The name `Player` is banned.

```bash
npm i -g @railway/cli && railway login --browserless
railway init --name teafabric-mc
railway add --service minecraft --image itzg/minecraft-server:java17 \
  --variables EULA=TRUE --variables VERSION=1.16.5 --variables TYPE=PAPER --variables ONLINE_MODE=FALSE \
  --variables MODRINTH_PROJECTS=authmereloaded --variables JVM_DD_OPTS=Paper.IgnoreJavaVersion=true \
  --variables MEMORY=512M --variables MALLOC_ARENA_MAX=2 --variables VIEW_DISTANCE=6 --variables ALLOW_NETHER=FALSE \
  --variables 'JVM_XX_OPTS=-XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m -Xss512k -XX:CICompilerCount=2 -XX:+ExitOnOutOfMemoryError' \
  --variables 'RCON_CMDS_STARTUP=ban Player The name Player is not allowed. Reload the page with ?rename at the end of the address and choose your own name.'
# add a volume mounted at /data to the minecraft service (dashboard, or the volumeCreate API)
mkdir relay && cp deploy/railway/relay/package.json proxy/ws-proxy.js relay/ && cd relay
railway add --service relay --variables 'MC_TARGET=${{minecraft.RAILWAY_PRIVATE_DOMAIN}}:25565' \
  --variables ALLOWED_ORIGIN=https://<your page's host>,http://127.0.0.1:47265
railway up --service relay && railway domain --service relay
```

`EULA=TRUE` accepts Mojang's EULA. In the game, connect to `wss://<relay domain>/`.

- **Java and Paper**: AuthMe 5.7 (the release for 1.16.5) needs Java 17, while Paper 1.16.5 refuses
  Java 17 unless started with `-DPaper.IgnoreJavaVersion=true`; that combination runs fine.
- **Memory**: with a 1 GB service limit, Paper with a 768 MB heap was killed for exceeding it (exit code
  -1, no Java error). The settings above (512 MB heap, serial GC, capped metaspace/code cache/direct
  memory, two malloc arenas, no Nether) keep the container at about 750 MB with a player online.
- **Changing the ban message**: Minecraft keeps the first reason of a ban. To change it, put
  `pardon Player` on the line before `ban Player …` in `RCON_CMDS_STARTUP` (the variable takes one
  command per line).
- **Players share one IP**: everyone reaches the server through the relay, so the server sees the
  relay's address for all of them. Two config patches account for that (`deploy/railway/patches/`,
  fetched with `APPLY_EXTRA_FILES` and applied by `PATCH_DEFINITIONS`): AuthMe allows any number of
  registrations per IP (it allowed one, so only one player could ever register) and no longer tells a
  newcomer the names of the other accounts on "their" IP, and Bukkit's connection throttle is off
  (it refused a second join within 4 s). `APPLY_EXTRA_FILES` keeps files that already exist, so a
  changed patch needs a new file name.
  ```bash
  B=https://raw.githubusercontent.com/<owner>/<repo>/<commit>/deploy/railway/patches
  railway variables --service minecraft --set "APPLY_EXTRA_FILES=teafabric/patchdefs<$B/authme-shared-ip.json
  teafabric/patchdefs<$B/bukkit.json" --set PATCH_DEFINITIONS=/data/teafabric/patchdefs
  ```
- **Server list look**: `ICON=<URL of deploy/railway/server-icon.png>` (64×64 pixel-art tea cup) and a
  two-line coloured `MOTD`; write `§` codes and non-ASCII text as `\uXXXX` escapes (server.properties
  is read as ISO-8859-1), and `\n` for the second line.
- **Spawn**: a 21×21 stone-brick plaza at the spawn point with paths, hedges, lanterns and signs
  explaining `/register` and `/login`; `spawnRadius 0` puts every player in its centre, and the default
  spawn protection (16 blocks) keeps it intact. It was built once with console commands in
  `RCON_CMDS_STARTUP` (absolute coordinates, `forceload` around them), which was then set back to the
  ban. In sign text, write non-ASCII as `\\\\uXXXX`: one level of backslashes is lost before the
  command reaches the server, and the SNBT string needs `\\u` for the JSON `\u`.
- **Our server in the web client's list**: `SERVER_ADDRESS` given to `scripts/deploy-cloudflare.sh`
  (and `SERVER` to `scripts/package-desktop.sh`) writes `servers.json`; on a player's first visit the
  page adds that server at the top of the multiplayer list (once: a player who removes it keeps it
  removed). `SERVER_NAME` sets its name (default "TeaFabric 伺服器").
- **Whitelist**: to limit who can join at all, add `ENABLE_WHITELIST=TRUE` and `WHITELIST=Name1,Name2`.
- **Forgotten passwords**: an operator can run `authme unregister <name>` on the server console (or add
  it to `RCON_CMDS_STARTUP` once); a logged-in player can drop their own account with
  `/unregister <password>` and change it with `/changepassword <old> <new>`.

Tested from the page on Cloudflare: `Player` is refused with the ban message; a new name is asked to
`/register`, registers, and after reloading the page is asked to `/login` and gets in.

## Multiplayer

**Player name**: the loading page asks for one the first time (3–16 letters, digits or `_`; `Player` is
refused) and the browser keeps it. Add `?rename` to the address to choose another, or `?username=Name` to
use a name for that page only. In offline-mode servers the name is all that identifies a player, so use a
login plugin there (see [the Railway server](#a-server-for-the-web-client-on-railway)).

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

## Singleplayer

Singleplayer runs Minecraft's own integrated server inside the page, on green threads next to the
client: Singleplayer → Create New World works as usual.

* **Worlds are kept** in the browser's storage (IndexedDB), with the options, the server list, config
  and resource packs: files are written back a few seconds after the game saves them (Minecraft saves
  a world when the game is paused and when it is left, so use *Save and Quit* or pause before closing
  the tab). `?nopersist` starts without saved data and keeps nothing; `?resetstorage` deletes it first.
  Clearing the site's data in the browser removes it too.
* **Smaller spawn area**: vanilla generates 21×21 chunks around the spawn before a new world opens and
  keeps them loaded forever. In the page the radius is 5 (9×9 chunks) by default, which brings world
  creation from about 160 s to about 55 s (headless Chromium, software WebGL) and leaves the server less
  to tick; `?spawnRadius=11` restores vanilla behaviour (1–11).
* **Backups / moving worlds**: *Open World Folder* (Edit World) downloads the world as a zip, *Make
  Backup* followed by *Open Backups Folder* downloads the backup; other "Open … Folder" buttons work the
  same way and web links open in a new tab. Dropping a world zip on the Select World screen imports it.

Tested in headless Chromium: create a world (spawn area ≈50 s), Save and Quit, reload the page, the
world is listed with its icon and opens at the same position; Open World Folder downloads
`New World.zip`, Make Backup + Open Backups Folder downloads the backups, and dropping
`New World.zip` back on the list adds "New World (2)", which opens.

How it works:

* the client and the integrated server talk over a pair of in-memory Netty channels (Netty's local
  transport needs event-loop threads), pumped once per frame;
* worker tasks submitted by the server (world generation, region-file I/O) run on a worker green thread
  that yields after every task, and the server's task loop yields too, so the page never freezes;
* fixes for TeaVM's in-memory file system and class library that saving needs: files opened `"rw"`
  are no longer truncated, `FileChannel.open(CREATE)` creates files, direct buffers are zeroed,
  `Deflater` accepts `Z_BUF_ERROR`, `Files.newByteChannel`, multi-dimensional `Array.newInstance`;
* the server's RSA key pair is skipped (only online-mode logins over the network use it).

Performance (headless Chromium, SwiftShader software WebGL, 4 cores): about 6 fps while playing;
server ticks of 100–150 ms with the default spawn area (≈200 ms with the vanilla one) instead of 50 ms —
the client and the server share one browser thread. A real GPU helps the frame rate; a lower render
distance helps both.
"Open to LAN" is impossible in a browser.

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

* **Resource packs**: drag the pack `.zip` onto the page while the Resource Packs screen is open,
  confirm, select it, Done (tested: a pack's language file renamed the title screen buttons). Packs are
  kept in browser storage like worlds.
* **Data packs**: drag them onto Create World → Data Packs (singleplayer; the same screen as resource
  packs, not tested separately), or install them on a server.
* **Server-side mods and plugins** of the server you connect to (anything that needs no client code).

Rebuilding after adding jars to `./mods` takes about 15–25 minutes (bake + TeaVM + packaging), and
the result is a new static `build/web` directory to serve.

### Optimization mods

Sodium 0.2.0, Lithium 0.6.6, Phosphor 0.8.0 and FerriteCore 2.1.1 (the 1.16.5 jars, in `./mods`) run in
the browser build. Each relied on something a browser build does not have; the build adapts them
automatically, and only when the mod is present (`baker/.../ModCompat.java` rewrites the few methods
involved while baking; `SodiumCompat` and the WebGL emulation do the rest):

* **FerriteCore** defines two classes inside Guava's package at run time and calls through method handles
  (its compact block state property map, the block state cache); its config reaches its file handler
  through reflection. The classes are defined at build time and the calls are direct.
* **Lithium**: its initializer checked a config that its mixin plugin (which runs at build time here) fills;
  that check is dropped. It asks entity classes whether they override two collision methods
  (`getDeclaredMethod`); those methods keep reflection metadata (`TeaFabricReflection`).
* **Phosphor** uses `StampedLock`, which TeaVM's class library lacks: added for green threads
  (`compiler/src/runtime`).
* **Sodium** renders with its *Oneshot* backend (one draw per chunk section; its multi-draw backend needs
  OpenGL 4.3 indirect draws, which WebGL 2 does not have) through the emulation's vertex array objects.
  Its chunk meshing runs on one worker green thread that lets the page draw a frame every 6 ms (with a
  thread per core, all sharing the page's one thread, no frame was drawn until every chunk was built).
  WebGL has no alpha test, so the GLSL translator adds it to every fragment shader (leaves, grass and
  flowers had solid backgrounds); fog and other state Sodium reads back from GL is answered by the
  emulation; its video settings no longer offer Fabulous!; its F3 memory line shows the JavaScript heap.
  Sodium finds no `sun.misc.Unsafe` and uses its NIO vertex writers.

Measured in headless Chromium with software rendering, the same build with and without the four mods
(two runs each, a new random world each run): a new world was ready 22–24 s after Create World with the
mods, 46–47 s without. Frame rates 20 s after joining varied more between random worlds (2.9–6.5 fps either way) than between the builds, because chunks were still loading.

**Iris (shader packs) does not work** and is not a matter of adapting the mod: shader packs are written for
desktop OpenGL's compatibility profile (built-in matrices and vertex inputs such as `gl_ModelViewMatrix`
and `gl_Vertex`, up to eight render targets, shadow maps, and in newer packs compute shaders and image
load/store), while WebGL 2 runs GLSL ES 3.00 without any of the built-ins, compute shaders or image access.
Every pack would need a translator for all of that, and a pack renders the world a second time for its
shadow map and adds a dozen full-screen passes per frame, on a page that already spends most of its
frame in Minecraft's own code. WebGPU would not change this: it has no compatibility profile either, needs
its own shading language (WGSL), and is missing on many of the older devices this is played on.

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

Page switches (debugging): `?log` (log overlay), `?username=Name`, `?rename` (ask for the player name again), `?threads` (Minecraft worker pools
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
* Sound: effects and streamed music through WebAudio, decoded by the browser (tested in Chromium:
  button clicks, menu music, footsteps and other sounds in a world).
* Every language: switching to 繁體中文 takes under a second (one translation file is fetched).
* Graphics: Fast and Fancy. Fabulous! is not offered (its translucency post-processing chain is not
  supported by the WebGL emulation and rendered worlds black); Fabulous in an old options.txt loads as
  Fancy, and the GPU warnlist screen is off (the emulation reports OpenGL 2.1, so it always warned).
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
* Singleplayer is slow: the integrated server shares the page's only thread with the client.
* Multiplayer needs a WebSocket ↔ TCP proxy (browsers cannot open TCP sockets); online-mode
  authentication is not available.
* Performance: the module is compiled with TeaVM's `ADVANCED` optimisation (72 MB instead of 106 MB with
  `SIMPLE`; measured in headless Chromium: title screen after 11.0 s instead of 12.9 s, a new world ready
  after 43 s instead of 63 s). The WebGL emulation skips redundant calls: it keeps the state it last
  set (texture parameters per texture, pixel store, buffer and texture bindings, attribute pointers and
  values) and each fixed-function program remembers its uniform values. Counted in a singleplayer world,
  WebGL calls per frame fell from about 9,000 to about 1,450 (texParameteri 2,981 to 9, pixelStorei 2,911
  to 135, mostly from animated textures), and the frame rate under software rendering from 3.7 to 6.5.
* Lighter video settings for the browser (`WebDefaults`): once per player, options still at vanilla's
  default become render distance 6, biome blend 3×3, mipmap levels 2, fast clouds and decreased
  particles; options a player changed are kept.
* WebGPU: not used. A CPU profile of a world shows the time in Minecraft's own code and the browser, not
  in the GL emulation (0.3%), so a WebGPU backend would not make the game itself faster, and the slow
  devices this is played on often have no WebGPU at all.
