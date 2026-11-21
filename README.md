# TeaFabric-1.16.5

Minecraft Java Edition 1.16.5 with Fabric mods, compiled ahead of time to **WebAssembly GC** with **TeaVM 0.16**
and played in a browser page: singleplayer with worlds kept in the browser, multiplayer through a WebSocket
relay, sound and music, every language, the mods of Fabulously Optimized and Iris shader packs.

## 中文說明

**TeaFabric** 讓 Minecraft Java 版 1.16.5（加上 Fabric 模組）直接在瀏覽器裡跑：打開網頁就能玩，不用裝 Java，也不用
啟動器。遊戲和模組在建置時由真正的 Fabric Loader 套用好所有 Mixin，再用 TeaVM 編譯成一個 WebAssembly 模組；
OpenGL、音效、視窗和檔案系統換成瀏覽器的 WebGL 2 / WebGPU、WebAudio、DOM 和 IndexedDB。

### 線上體驗 / Try it

| 版本 | 內容 | 第一次下載 | 網址 |
|---|---|---|---|
| **兼容版** | Fabulously Optimized 全套模組、Iris 光影、REI / WTHIT / AppleSkin 等輔助模組 | 約 43 MB | https://teafabric.teafabric-871743.workers.dev/ |
| **效能版** | 只有四個效能模組；單人世界在 Web Worker（另一條執行緒）裡跑，比較順 | 約 32 MB | https://teafabric-perf.teafabric-871743.workers.dev/ |
| **下載版** | 兼容版，Windows / macOS / Linux，下載一次（約 141 MB，可續傳），之後在本機開 | — | https://teafabric.teafabric-871743.workers.dev/download/ |

- **QQ 群 1063719613** · **Discord** https://discord.gg/GGN6TUSdNN
- 網站有**密碼**（頁面含 Minecraft 的程式和素材，不能公開），請在 QQ 群或 Discord 索取，使用者名稱隨便填。
- 之後再開只下載有更新的部分。兩個版本的載入畫面都有連到另一版的連結。
- 要用電腦（鍵盤加滑鼠），手機不支援。瀏覽器要支援 WebAssembly GC：Chrome / Edge 119 以上（Firefox 120+、
  Safari 18.2+ 理論上可以，但沒測過）。

**選哪個版本**：要光影或那些模組就用兼容版；電腦普通、網路慢、想要流暢就用效能版。

### 怎麼玩

- **名稱**：第一次開要輸入玩家名稱（3–16 個英文字母、數字或 `_`，不能用 `Player`）。網址後面加 `?rename` 可以改名。
- **多人**：「多人遊戲」清單裡已經有我們的伺服器。第一次進去輸入 `/register 密碼 密碼`，之後每次 `/login 密碼`。
  出生點是一座城鎮：工作坊、商店街、新手服務處（領新手禮包）、跑酷塔、野外傳送門；輸入 `/trigger help` 看指令
  （`/spawn`、`/sethome`、`/home`、`/tpa`、`/back`、金鏟子圈地等）。
- **單人**：「單人遊戲 → 建立新世界」。世界存在瀏覽器裡。遊戲每隔幾分鐘自動存檔，暫停（Esc）和「儲存並離開」時也會存，
  幾秒後寫進瀏覽器，所以**關分頁前先按 Esc**。清除網站資料會把世界刪掉；兩個版本（和下載版）的世界是分開存的。
  備份：「編輯世界 → 開啟世界資料夾」會下載世界 zip；把世界 zip 拖到「選擇世界」畫面就能匯入。
- **光影（兼容版）**：在世界裡按 **O** 打開光影包畫面，把光影包 zip 拖進網頁，選它，按 Apply / Done。MakeUp Ultra
  Fast 最輕；BSL 只在 WebGPU 下測得動。用光影建議在載入畫面按「Use WebGPU」（會記住，`?webgl` 可以改回）。
  光影很吃效能，卡的話就關掉。
- **資源包**：在「資源包」畫面把 zip 拖進網頁。
- **模組**：網頁裡不能自己加模組（模組在建置時就編進去了），想要新模組只能重新建置。

### 卡、閃、畫面停住怎麼辦

- **卡頓**：最常見的是走進沒去過的地方、載入新區塊的時候（測試機上偶爾停 0.5–1 秒）。調低「視距」（預設 6）、關掉光影，
  或改用效能版（世界在另一條執行緒，這種停頓大約少一半）。
- **閃爍**：渲染重的時候會閃黑或閃天空色的問題已經修好；還會閃的話重新整理頁面，拿到新版本。
- **跳出「畫面停止更新了」**：瀏覽器重置了顯卡渲染（通常是顯存不夠或顯卡驅動逾時）。單人世界會先自動暫停存檔，
  按「重新載入」繼續。常發生的話調低視距、關掉光影。
- **回報問題**：請說明用哪個版本、WebGL 還是 WebGPU（載入畫面上有寫）、當時在做什麼；可以的話按 F12，把 Console
  裡的紅字截圖給我們。網址加 `?log` 會把記錄顯示在畫面上。

### 目前做不到 / 已知問題

- 不能用正版帳號登入，只能進離線模式（offline-mode）的伺服器，而且要透過 WebSocket 中繼。沒有「在區域網路公開」。
- 「建立新世界 → 資料包」會失敗；把資料包放進世界 zip 的 `datapacks/` 資料夾再匯入可以用。
- WebGPU：截圖（F2）和世界縮圖是黑的；準星對著腳邊的方塊時，選取框的線會畫歪。
- 需要幾何著色器的光影包不能用。
- 效能版的世界清單沒有縮圖。
- 只在 Chrome / Chromium 上測過，所有數字都來自測試機（軟體渲染），沒有在真實顯卡上量過。

The demo is password-protected because the page contains Minecraft's code and assets; ask for access on
[Discord](https://discord.gg/GGN6TUSdNN) or in QQ group 1063719613. See
[Hosting on Cloudflare](#hosting-on-cloudflare-password-protected) to run your own.

## Legal

Minecraft is proprietary software owned by Mojang/Microsoft. This repository contains **no**
Minecraft code or assets. Everything Mojang-derived (client jar, libraries, assets, the baked jar,
the compiled `classes.wasm`, `build/web/data/*.zip`) is downloaded or produced locally into
git-ignored directories (`.cache/`, `build/`). Use the result only with your own copy of the game
and do not publish the built web bundle.

## Two editions

The two hosted sites are built from two branches of this repository:

| | compatibility edition (this branch, `ccr-a6ade281-dizv9f`) | performance edition (`perf-1.16.5`) |
|---|---|---|
| mods | Fabric API and 46 jars in `./mods`: the 45 that `scripts/get-mods.sh` downloads (Fabulously Optimized 1.12.3, REI, WTHIT, AppleSkin, Mouse Wheelie, BetterF3, ShulkerBoxTooltip) and ViaFabric, added by hand (see Server protocol); the title screen counts 141 mods with Fabric API's modules and bundled libraries | Sodium, Lithium, Phosphor and FerriteCore, no Fabric API, plus TeaFabric's own `webmod` |
| shader packs (Iris) | yes | no |
| singleplayer server | green threads on the page's thread, next to the client | a Web Worker (`?noworker`: on the page) |
| Sodium's chunk draws | one draw call per range | one `WEBGL_multi_draw` call per chunk |
| first download (release build, gzip) | 42.8 MB, of which 18.5 MB game code | 31.6 MB, of which 11.0 MB game code |
| title screen on the test machine | 40–50 s | about 13 s |
| TeaVM compile on 4 cores | 35–40 min, about 12 GB of RAM | about 15 min |

The test machine throughout this README: headless Chromium, SwiftShader software rendering, 4 cores. Nothing
was measured on a real GPU.

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
| Canvas presentation | Minecraft clears the canvas at the start of a frame and draws the finished picture into it at the end; when the game thread yields in between (chunk building, lock waits, the integrated server), the browser showed the cleared canvas: a flash, in two frames out of three while walking into new chunks. `boot.js` defers clears of the canvas to its next use, with the clear state of the moment (`?eagerclear`: as before). A lost WebGL context / WebGPU device (driver reset under heavy load) used to freeze the picture silently; the page now releases the mouse (Minecraft pauses, which saves a single-player world) and offers a reload |
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

Requirements: JDK 21, Gradle 8.14 (or the wrapper), Python 3, Go (only for the downloadable version) and network
access to piston-meta/piston-data/libraries/resources (Mojang), meta/maven.fabricmc.net and Maven Central. For the
compatibility edition's full mod set, a machine with 16 GB of RAM: TeaVM's compiler heap is the machine's memory
minus 4 GB, between 6 and 12 GB (`TEAVM_HEAP=8g` overrides), and this mod set uses about 12 GB. A full build takes
about an hour on 4 cores: baking about a minute, each TeaVM compile 35–40 minutes, and the first compile after the
mod set changed usually finds APIs to stub and compiles again. The performance edition builds in about 15 minutes.

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
downloads about 43 MB (the performance edition 32 MB) instead of about 150 MB, and the Worker lets browsers keep `c/`, `sounds/` and
`lang/` for a year, so later visits download only what a new build changed (tested: a second visit
fetched none of the large files again). The same Worker relays multiplayer
connections: use `wss://<worker>.workers.dev/relay?target=mc.example.com:25565` as the server address
(not tested yet; the free plan's CPU limits may cut busy connections).

**Release and debug builds**: TeaVM's module carries a "name" section with the Java name of every
function, local, global, type and field (45 % of the file), and the runtime downloads `classes.wasm.teadbg` at
start-up to turn stack traces back into Java. `scripts/split-debug.py` (run by `scripts/build-web.sh`) splits
`build/web` into a release build, `teavm/classes.wasm` without the name section, which the page loads, and the
build as compiled in `teavm/debug/` with the `.teadbg`, which the page loads with `?debug`. The code is the same.
Compressed first downloads of the game code:

| | before | release (default) | `?debug` |
|---|---|---|---|
| compatibility edition (FO mods) | 25.6 MB + 3.6 MB `.teadbg` | 18.5 MB | 25.6 MB + 3.6 MB |
| performance edition | 14.2 MB + 2.3 MB `.teadbg` | 11.0 MB | 14.2 MB + 2.3 MB |

The resource archives (`classpath.zip`, `assets.zip`) are unchanged; the downloadable version holds only the release build.

**Two editions linked to each other**: `EDITION_NAME=... OTHER_EDITION_URL=https://... OTHER_EDITION_TEXT=...`
writes `edition.json`; the loading panel then shows the edition's name next to the title and a link to the
other site. The two hosted sites are deployed with:

```bash
SERVER_ADDRESS=wss://relay-production-e77b27c1.up.railway.app/ EDITION_NAME=兼容版 \
  OTHER_EDITION_URL=https://teafabric-perf.teafabric-871743.workers.dev/ \
  OTHER_EDITION_TEXT="想要更快、下载更小？前往效能版（只带性能模组，单人世界在 Web Worker 里运行）" scripts/deploy-cloudflare.sh
# from the perf-1.16.5 branch:
WORKER_NAME=teafabric-perf LAUNCHER=0 SERVER_ADDRESS=wss://relay-production-e77b27c1.up.railway.app/ EDITION_NAME=效能版 \
  OTHER_EDITION_URL=https://teafabric.teafabric-871743.workers.dev/ \
  OTHER_EDITION_TEXT="需要光影和更多模组？前往兼容版（Fabulously Optimized 全套 + Iris 光影）" scripts/deploy-cloudflare.sh
```

**Downloadable version**: `/download/` (behind the same password) offers the game as a zip for people
who would rather not load 150 MB from the site every time, e.g. on slow connections. It holds `build/web`
plus a small launcher (`launcher/`, Go) for Windows, macOS and Linux that serves it on
`http://127.0.0.1:47265/` and opens the default browser; the port is fixed because the browser keeps
worlds per address. `scripts/package-desktop.sh` builds it (`build/TeaFabric-1.16.5.zip`, about 141 MB with
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
  --variables 'JVM_XX_OPTS=-XX:+UseSerialGC -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=48m -Xss512k -XX:CICompilerCount=2 -XX:+ExitOnOutOfMemoryError' \
  --variables 'RCON_CMDS_STARTUP=minecraft:ban Player The name Player is not allowed. Reload the page with ?rename at the end of the address and choose your own name.'
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
- **Spawn town** (`deploy/railway/datapack/`, 出生點城鎮): a datapack built around the plaza, so the server is
  more than plain survival. `python3 deploy/railway/datapack/build_town.py` generates it
  (`teafabric-town/` and `teafabric-town.zip`; the zip is committed although `*.zip` is ignored).
  - **Layout:** a walled 65×65 square with gates north, south and west.
    - north-west: a workshop, free for everyone: crafting tables, furnaces, anvils, a grindstone, brewing, an
      enchanting table with 15 bookshelves (level 30), and ender chests for personal storage.
    - north-east: a market with six villager traders. Players sell stone, ores, wood, crops and mob drops for
      emeralds, and buy food, tools, enchanted books and Nether materials (the Nether is off on this server).
    - south-east: a newcomers' hall with the rules, help, and a pad that gives a starter kit and a guide book
      once per player.
    - south-west: a parkour tower that gives 3 diamonds once per player.
    - the east gate is a portal that sends players to a random place up to 2000 blocks away.
  - **Rules inside the wall:** players are in adventure mode (blocks can be used but not broken or placed) and
    hostile mobs are removed. Outside the wall it is ordinary survival.
  - **Player commands:** `/trigger spawn` (back to spawn) and `/trigger help`. An operator who wants to edit
    the town can `/tag <name> add tf_builder` to keep their game mode inside.
  - **Install:**
    ```bash
    railway variables --service minecraft --set REMOVE_OLD_DATAPACKS=TRUE --set SPAWN_PROTECTION=0 \
      --set "DATAPACKS=https://raw.githubusercontent.com/<owner>/<repo>/<commit>/deploy/railway/datapack/teafabric-town.zip"
    ```
    `SPAWN_PROTECTION=0` because vanilla spawn protection also stops non-operators from using crafting tables
    and furnaces; adventure mode protects the town instead.
  - **Building:** the load function force-loads the town's chunks and builds 10 s later (fills in unloaded
    chunks fail silently: the first version left the north half as forest). `BUILD_VERSION` in the script
    decides whether a server builds it again after a change.
- **Helper plugins** (輔助插件), all openly licensed and checked to load on Paper 1.16.5 with Java 17:

  | plugin | what players get |
  |---|---|
  | EssentialsX 2.21.2 + EssentialsXSpawn (GPL-3.0) | `/sethome` `/home` (3 homes), `/spawn`, `/tpa` `/tpahere` `/tpaccept`, `/back` (also to where you died), `/msg` `/r` `/mail`, `/afk`, `/ignore`, `/seen`, `/suicide`, `/warp`; messages in Traditional Chinese; teleports wait 3 s |
  | GriefPrevention 16.17.1 (GPL-3.0) | land claims with a golden shovel (in the starter kit): others cannot build or open containers inside; `/trust`, `/abandonclaim`, `/claimslist` |
  | GravesX 4.9.8.3 (GPL-3.0) | a grave keeps your items and experience where you died |
  | OnePlayerSleep 4.0.1 (MIT) | one player sleeping skips the night (and the rain) |
  | ChestSort 1.3 (MIT) | double-click an empty slot to sort a chest, barrel, shulker box or your inventory |

  ```bash
  B=https://raw.githubusercontent.com/<owner>/<repo>/<commit>/deploy/railway
  railway variables --service minecraft \
    --set "MODRINTH_PROJECTS=authmereloaded,essentialsx:Oa9ZDzZq,essentialsx-spawn:RVbLg2Am,oneplayersleepgg:aOoFYmeO,gravesx:5GqMoSyc,chestsort:zcgLnb9L" \
    --set "PLUGINS=https://mediafilez.forgecdn.net/files/3173/411/GriefPrevention.jar"
  # after that first start (the plugins create their configuration and data folders):
  railway variables --service minecraft --set "APPLY_EXTRA_FILES=teafabric/patchdefs<$B/patches/authme-shared-ip.json
  teafabric/patchdefs<$B/patches/bukkit.json
  teafabric/patchdefs<$B/patches/essentials.json
  teafabric/patchdefs<$B/patches/oneplayersleep.json
  teafabric/patchdefs<$B/patches/chestsort.json
  plugins/GriefPreventionData/ClaimData<$B/griefprevention/1.yml"
  ```
  - **Versions are pinned** (Modrinth version IDs) because the newest builds do not run on this server, although
    Modrinth lists 1.16.5 for them:
    - EssentialsX 2.22 relies on the MiniMessage library that Paper only ships from 1.18 ("Error occurred while
      enabling Essentials", then every EssentialsX command, `ban` included, fails); 2.21.x bundles its own copy.
    - The newest OnePlayerSleep is compiled for Java 25 and the newest Sleeper partly for Java 21; this
      server runs Java 17.
    - GriefPrevention's 1.16 release (16.17.1) is only on BukkitDev and GitHub.
  - **The patches** (`deploy/railway/patches/`, applied by `PATCH_DEFINITIONS` before each start, so they
    need the plugins' first start):
    - EssentialsX: with no permissions plugin, ordinary players get exactly the commands in
      `player-commands`, once `use-bukkit-permissions` is false. The list leaves out kits (the default `tools`
      kit could be taken every 10 seconds), the money commands (the town trades in emeralds), nicknames and
      `/world` (it would reach the End without eyes of ender). The patch also sets `locale: zh_TW`, a 3-second
      teleport delay, and no kit for newcomers (the town gives one).
    - OnePlayerSleep: also clears the weather, with a Chinese message.
    - ChestSort: an empty permission, because otherwise only operators can sort.
  - **The town's admin claim** (`deploy/railway/griefprevention/1.yml`, the town and the roads out of its gates):
    players cannot claim land in the town (a claimed workshop would lock everyone else out of its furnaces), and
    everyone may use its containers, buttons and traders. Install it only after GriefPrevention has started once
    and created its data folder. On every start GriefPrevention logs "nextClaimID was lesser or equal to an
    already-existing claim ID" and moves the next ID past it; this is harmless.
  - **Start-up commands:** `RCON_CMDS_STARTUP` uses `minecraft:ban` / `minecraft:pardon`, because EssentialsX
    replaces `ban`. Adding `gc` there prints the TPS and heap use to the log on each start.
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

Singleplayer runs Minecraft's own integrated server inside the page: in the compatibility edition on green threads
next to the client, in the performance edition in a Web Worker (see that branch's README). Singleplayer → Create
New World works as usual.

* **Worlds are kept** in the browser's storage (IndexedDB), with the options, the server list, configs and
  resource packs: files are written back a few seconds after the game saves them. Minecraft saves a world every
  few minutes, when the game is paused and when it is left, so pause or *Save and Quit* before closing the tab.
  Each site keeps its own storage: the two editions and the downloadable version do not share worlds.
  `?nopersist` starts without saved data and keeps nothing; `?resetstorage` deletes it first. Clearing the
  site's data in the browser removes it too.
* **Smaller spawn area**: vanilla generates 21×21 chunks around the spawn before a new world opens and keeps
  them loaded forever. In the page the radius is 5 (9×9 chunks) by default; `?spawnRadius=11` restores vanilla
  (1–11). On the test machine a new world opens 22–28 s after Create New World.
* **Backups / moving worlds**: *Open World Folder* (Edit World) downloads the world as a zip, *Make
  Backup* followed by *Open Backups Folder* downloads the backup; other "Open … Folder" buttons work the
  same way and web links open in a new tab. Dropping a world zip on the Select World screen imports it.

Tested: create a world, Save and Quit, reload the page, the world is listed with its icon and opens at the same
position; Open World Folder downloads `New World.zip`, and dropping it back on the list adds "New World (2)",
which opens.

How it works:

* the client and the integrated server talk over a pair of in-memory Netty channels (Netty's local
  transport needs event-loop threads), pumped once per frame;
* worker tasks submitted by the server (world generation, region-file I/O) run on a worker green thread
  that yields after every task, and the server's task loop yields too, so the page keeps drawing (with the
  stalls under Status while new terrain generates);
* fixes for TeaVM's in-memory file system and class library that saving needs: files opened `"rw"`
  are no longer truncated, `FileChannel.open(CREATE)` creates files, direct buffers are zeroed,
  `Deflater` accepts `Z_BUF_ERROR`, `Files.newByteChannel`, multi-dimensional `Array.newInstance`;
* the server's RSA key pair is skipped (only online-mode logins over the network use it).

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
* **Data packs**: Create World → Data Packs does not work in the browser yet: the screen needs
  `Files.createTempDirectory`, which the web build lacks, and shows "Failed to copy packs". What works
  (tested with the spawn-town datapack): put the pack's `.zip` into a world zip's `datapacks/` folder and
  drop the world zip on the Select World screen. The pack loads when the world is opened. Or install
  data packs on a server.
* **Server-side mods and plugins** of the server you connect to (anything that needs no client code).

Rebuilding after adding jars to `./mods` takes about an hour with the full set on 4 cores (see Building), and
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
  (`getDeclaredMethod`); those methods keep reflection metadata (`TeaFabricReflection`). Its
  point-of-interest search streams a `BitSet` into `flatMap`, which exposed a TeaVM bug: `BitSet.stream()`
  repeated its current bit forever under a lazy consumer, so a bee looking for a hive collected the same
  nests until the array overflowed and the page froze for minutes. `BitSet.stream` is redirected to a
  correct version (`LangCompat`).
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
* **World generation** runs on Minecraft's worker pool, which is green threads here. With Lithium, a task
  sometimes waited for a task queued behind it on the same thread, and about one new world in six stopped
  at "Preparing spawn area" for good (both the worker and the server thread waiting in `CompletableFuture`,
  found with `?waits`). Every untimed wait in the concurrency shims reports to the pool, which starts
  another thread (up to 8) when all of its threads wait inside tasks while tasks are queued, as a JVM's
  pool would have a free thread. In 12 new worlds afterwards, all were ready in 22–28 s; the extra thread
  was needed in 3 of them.

Measured in headless Chromium with software rendering (SwiftShader), the same build with and without the
four mods, a new random world each run:

* a new world was ready 22–24 s after Create World with the mods, 39–47 s without;
* WebGL calls per frame a minute into the world: 924 instead of 1,495. Sodium updates animated textures
  only while they are visible (texSubImage2D 458 per frame to none); its per-chunk draws set their
  attribute pointers each time (vertexAttribPointer 175 to 212);
* JavaScript heap a minute in: 420–444 MB with the mods, 466–498 MB without (FerriteCore);
* frame rate a minute in: 3–10 fps with the mods, 4–9 fps without, no difference these runs can show:
  software rendering draws the pixels on the CPU, and the random worlds differ more than the builds. On a
  real GPU the saved WebGL calls and texture uploads should count for more; that was not measured.

**Iris (shader packs) works** with packs written for desktop OpenGL, with both renderers (WebGL 2 and WebGPU, see
below). In a world, press O (Iris' Shader Packs screen), drop the pack's `.zip` onto the page, select it and press
Apply or Done; packs are kept in browser storage like worlds. Tested in headless Chromium with software rendering
(SwiftShader):

* **MakeUp Ultra Fast** renders with WebGL 2 and with WebGPU, the two alike on the same seed: shader lighting and
  colour grading, fog, sun haze, translucent leaves, with the HUD, WTHIT and AppleSkin on top.
* **BSL** (HIGH profile) renders with WebGPU: soft lighting and shadows, sky glow, colour grading. With WebGL 2 all
  of its programs (shadow, 15 gbuffers, deferred, 7 composite, final) translate and link and a frame is drawn
  into Minecraft's main framebuffer (checked by reading its pixels back), but the software-rendering test
  browser then ran out of memory (7 GB) and was killed.
* Neither was tested on a real GPU.

**WebGPU for shader packs**: the loading panel has a "Use WebGPU" button (remembered in the browser; `?webgpu` and
`?webgl` in the address override it). For shader packs the WebGPU layer (`web/webgpu`) gained:

* in the shader conversion (`glsl-vk.js`): functions that take a sampler are specialised per sampler they are
  called with (naga's GLSL front end takes no opaque parameters; packs pass `noisetex` to helpers); matrix and array
  varyings cross between stages as their columns and elements (WebGPU passes only scalars and vectors); texture
  lookups in vertex shaders read level 0 (implicit derivatives are fragment-only); samplers the code does not use
  are left out, as WebGL does (packs declare dozens, more than WebGPU's 16 per stage); the generated WGSL turns off
  the derivative uniformity check (packs sample inside branches). An offline test converts all 105 programs of
  MakeUp, BSL and the game itself to WGSL with naga;
* in the renderer (`webgpu-gl.js`): render target formats (16- and 32-bit float, RGB10_A2, R11F_G11F_B10F, RG8,
  R16F…; 32-bit float ones need the float32-filterable and float32-blendable features, half floats otherwise),
  `glDrawBuffers` (only the draw buffers are written and cleared), depth blits and depth copies (a pass that writes
  `frag_depth` from the source), copies between formats, `glGenerateMipmap`, `glGetActiveUniform`, depth textures
  read through colour samplers (packs read `depthtex0` as `sampler2D`; an unfilterable binding), and a placeholder
  for a texture a pass samples while drawing into it (undefined in OpenGL, an error in WebGPU).

With WebGPU, `glReadPixels` still returns nothing: screenshots (F2) and world icons come out black.

What it took:

* **A GLSL translator** (`platform/.../gl/glsl`): packs are written in compatibility-profile GLSL (1.10–1.50:
  `gl_Vertex`, `gl_ModelViewMatrix`, `gl_FragData`, `texture2D`, `shadow2D`, `ftransform`, built-in fog), WebGL 2
  runs GLSL ES 3.00. A preprocessor, parser and type checker (with GLSL's implicit int-to-float conversions)
  rewrite each shader: built-ins become `tf_*` inputs and uniforms that the emulation fills from its fixed-function
  state (matrices, client arrays, current colour and texture coordinates, fog), names GLSL ES reserves are
  renamed, global initialisers that are not constant move into `main`, the fixed-function alpha test is added, and
  varyings a fragment shader reads but its vertex shader does not write are declared when the program is linked.
  In an offline test it translates every program of four packs (BSL, MakeUp Ultra Fast, Mellow, Miniature Shader).
* **WebGL rules desktop GL does not have**, handled by the emulation: draw buffers the fragment shader has no
  output for are masked per draw (WebGL refuses the draw); depth copies (`glCopyTexImage2D` of depth, blits
  between depth formats) are framebuffer blits in matching formats; depth textures sample with NEAREST unless
  compare mode is on; a texture unit enabled with no texture bound is off, as in desktop GL (Iris unbinds every
  unit after its passes while one stays enabled, which turned the HUD and the whole screen black); depth cannot be
  read back, so Iris' centre depth (the focus of depth of field) is the far plane.
* **Java side**: `\R` in regular expressions (Iris splits pack files with it) and `\h` are spelled out for
  TeaVM's regex engine; JOML (in Iris) uses a plain number format (TeaVM's DecimalFormat rejects its pattern);
  the constants Iris adds to Sodium's enums at run time (a geometry shader type, vertex attributes) are made enum
  fields at build time, since TeaVM sizes `EnumMap` from an enum's fields (creating a world failed).

Not supported: packs that need geometry shaders (neither WebGL 2 nor WebGPU has them), and shader packs are heavy
for a browser page (the world is drawn a second time for the shadow map, then a series of full-screen passes);
under software rendering a frame took seconds.

### Fabulously Optimized and UI/helper mods

`scripts/get-mods.sh` puts the mods of Fabulously Optimized 1.12.3 (its last pack for 1.16.5) and a set of
UI and helper mods into `./mods` (list in `mods/README.md`). What they needed, besides what the
optimization mods above needed (all in `ModCompat`, `ArchitecturyEvents`, the class library and the compiler
plugin, applied only when the mod is present):

* **Architectury** (used by Cloth Config, BetterF3, Not Enough Crashes, REI): its
  `@ExpectPlatform` methods are invokedynamic calls with a bootstrap method that finds the Fabric
  implementation by name; they become direct calls. Its events build their invokers with
  `java.lang.reflect.Proxy` and call listeners through `MethodHandles`; for each of the 81 event interfaces
  the mods use, the baker generates the invoker class instead (same rules: a PASS result continues, any
  other result is returned), and the 100 call sites use it.
* **Not Enough Crashes** installs its platform layer and Smooth Boot reads its config from mixins injected
  at the head of Minecraft's `Main.main`, which the browser build does not go through: the injections are
  collected into a generated `MainHooks` that the launcher runs first. Its start-up downloads Yarn
  mappings, rewrites log events and installs a SecurityManager; those are skipped.
* **Configs** (AutoConfig, Gson, Jankson): the classes they fill by reflection (ConfigData implementations,
  classes passed to `fromJson`, and the classes of their fields) keep reflective fields and constructors
  (128 classes). TeaVM 0.16 could not generate reflection data for a field with annotations but no generic
  signature (AutoConfig's fields); the compiler plugin gives such fields their plain type. Annotation values
  (`@Config(name = ...)`) were broken by the plugin's own JNI stubbing, which now leaves TeaVM's generated
  annotation classes alone.
* **Classes loaded by name**: WTHIT creates its plugins from class names in `fabric.mod.json` and REI loads its
  modules with `Class.forName("...")`. The baker collects such names (custom metadata, string constants of
  classes that call `Class.forName`) into a generated lookup that `Class.forName` consults first, and keeps
  those classes and their constructors in the build. Night Config (in LambDynamicLights and LambdaBetterGrass)
  loads its file formats that way only to run their static initialisers, which register the file extension;
  its format detector registers them directly instead, and its file watcher (configs that reload when the file
  changes) watches nothing.
* Relative paths are relative to the game directory (`user.dir` and the file system's working directory),
  as with a desktop launcher (Entity Culling, Smooth Boot write `config/` that way). TeaVM's `java.nio.file`
  looked relative paths up from the root, so `Paths.get` / `Path.of` make them absolute there.
* Smaller ones: amecs' key binding map (in Mouse Wheelie; reflection on a mixin-added method) is a direct
  call; ShulkerBoxTooltip skips the validation of its config file (validators created by reflection); Dynamic FPS reads page focus and
  visibility through `glfwGetWindowAttrib`; Borderless Mining's undecorated window is the browser's full
  screen; WTHIT gets `java.awt.Rectangle`/`Dimension`, Mouse Wheelie `Color.RGBtoHSB`, BetterF3 a garbage
  collector bean, Iris's shader preprocessor an SLF4J binding to Log4j.
* Fields looked up by name on Minecraft and library classes, which the baker cannot see from the mods' code,
  keep reflection data in `TeaFabricReflection`: Slight GUI Modifications reads GlStateManager's current colour
  (`COLOR` and its `Color4` floats, found through `getClass()`) whenever it fades GUI textures in, and without
  them the client crashed with `NoSuchFieldException` (found while recording with shaders on); Minecraft's text
  deserialiser reads Gson's private `JsonReader.pos` and `lineStart` in a static initialiser, and without them
  the first JSON text read after start-up failed (the vanilla pack's `pack.mcmeta`: "Broken/missing
  pack.mcmeta").
* A mod entrypoint that fails is logged and the game continues with the others (Fabric Loader would throw
  after the stage, and Not Enough Crashes then skipped every later stage).

Light Overlay was tried and left out: it calls its own Fabric code through `MethodHandles` looked up with
class names built at run time.

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

## Page switches

Added to the page address (`…/?webgpu`, several with `&`):

| switch | effect |
|---|---|
| `?rename`, `?username=Name` | ask for the player name again; use a name for this page load only |
| `?webgpu`, `?webgl` | renderer for this load (the loading panel's button remembers a choice) |
| `?server=wss://…`, `?proxy=wss://…`, `?protocol=1.12.2` | connect right after start-up; send plain `host:port` addresses through a relay; server version through ViaFabric (compatibility edition) |
| `?spawnRadius=N` | spawn area of new worlds, 1–11 (default 5; 11 is vanilla) |
| `?nopersist`, `?resetstorage` | start without saved data and keep nothing; delete the saved data first |
| `?noworker`, `?nomultidraw` | performance edition only: worlds on the page instead of the worker; the old one-draw-per-range path |
| `?log` | log overlay on the page |
| `?debug` | the debug build: Java names in browser stack traces and profiles, Java stack traces resolved to source lines |
| `?debuglog` | DEBUG log level (start-up currently fails at that level, in `Bootstrap.logMissing`) |
| `?eagerclear` | clear the canvas when the game asks, not with the frame's picture (brings the old flicker back; for comparisons) |
| `?waits` | print the Java stack of any lock, future or park wait blocked for more than 5 s (finds green-thread deadlocks) |
| `?threads` | Minecraft's worker pools on green threads instead of inline |
| `?trace`, `?netdebug`, `?beacon`, `?probe` | GL upload/draw trace; stacks for disconnects; log lines sent to the HTTP server (survives renderer crashes); start-up probes |
| `?nogl`, `?noaudio`, `?nobuiltins`, `?nodeobf`, `?nofinalize`, `?noweak`, `?vanillaMain` | engine experiments: no rendering, no sound, no JS string builtins, no stack deobfuscation, no GC callbacks into Wasm, strong weak references, start through `net.minecraft.client.main.Main` |

## Status

Works (in both editions unless noted; tested on the test machine and played on the hosted sites):

* loading screen, title screen, every language (switching to 繁體中文 takes under a second), sound and music;
* singleplayer: create, play, save, reload the page and continue, back up and import worlds;
* multiplayer over `ws://` / `wss://`: 1.16.5 servers, older ones (1.8–1.16.4) through ViaFabric in the
  compatibility edition; chat, commands, text input with IME (Chinese) and the clipboard;
* the mods listed under [Two editions](#two-editions); Iris shader packs in the compatibility edition (MakeUp
  Ultra Fast with WebGL 2 and WebGPU, BSL with WebGPU);
* the hosted sites (password, download progress, long-term caching, a downloadable version) and the Railway
  server (logins, the spawn town, helper plugins).

Known issues and limits:

* **Stalls while new terrain loads**: walking into new chunks, single frames took 0.4–1.6 s on the test machine.
  A lower render distance helps, and the performance edition (the server's work leaves the page thread; such
  stalls about halved there).
* **The compatibility edition's singleplayer server shares the page's thread** with the client (server ticks
  took 100–150 ms instead of 50 ms in an earlier measurement on the test machine).
* WebGPU: `readPixels` is not implemented (screenshots and world icons are black), nor stencil or clears limited
  by a scissor; the block selection outline is drawn wrongly when the targeted block is next to the camera.
* Create World → Data Packs fails (it needs `Files.createTempDirectory`); put packs into a world zip instead.
* Shader packs that need geometry shaders do not work, and shader packs are heavy for a page.
* No "Open to LAN", no online-mode servers (no Microsoft login), multiplayer needs a WebSocket relay.
* `?debuglog` fails at start-up.
* Performance edition: worlds have no list icon; the worker is a second copy of the program (more memory).
* Only tested in Chromium and Chrome; not measured on real GPUs.

Fixed in November 2026:

* the screen flickered (the cleared canvas was shown) whenever the game thread yielded in the middle of a frame;
  while walking into new chunks that was two frames out of three (see "Canvas presentation" above);
* a lost WebGL context or WebGPU device froze the picture without a word; the page now saves and offers a reload;
* the compatibility edition crashed (`NoSuchFieldException`) when Slight GUI Modifications faded GUI textures in,
  for example on Iris' shader pack screen;
* "Broken/missing pack.mcmeta" at start-up in the compatibility edition (Gson's `JsonReader` fields).

## Engineering notes

* **TeaVM class-library bugs** found by running the same programs on the JVM and in the browser and diffing the
  output, all patched in `compiler/src/runtime`: `ArrayDeque` iterator removal, `ListIterator.add`,
  `java.util.Random` (now bit-identical to the JDK), `Math.round/rint/ulp/cbrt/log10`, `String.format` (`%%`,
  `%n`, `%f` rounding and `-0.0`), superclass-first static initialisation (in the `SplitClinits` pre-pass),
  `ConcurrentHashMap.newKeySet`, `Logger(String, String)`.
* **TeaVM 0.16.0 issues on the way to the title screen**, patched in `compiler/`: `@Async` implementations were
  coroutine-transformed when anything they call may suspend (`AsyncMethodFinder`; `Object.wait` never released
  its monitor, `requestAnimationFrame` was never called), contended monitors dropped all but one queued thread
  (`TObject.waitForOtherThreads`), interrupted waits resumed without the monitor, and `assert` statements were
  enabled (`desiredAssertionStatus`); ICU4J's property data is packaged for Minecraft's Bidi text code.
* **Renderer crash**: Chromium's renderer died when V8's optimising compiler ran out of zone memory
  (`V8 process OOM (Zone)`, Turboshaft `WasmGCOptimize`) tiering up the 230 KB Wasm function of
  `Blocks.<clinit>`; found with a gdb-attached renderer, a CDP trace and `--wasm-tier-up-filter`, fixed by the
  function splitters (see Compiler). Strict mode stays on because null dereferences relying on engine traps
  crash Chromium (reproduced with a 10-line TeaVM program).
* **Optimisation level**: TeaVM's `ADVANCED` (72 MB instead of 106 MB with `SIMPLE` at the time; title screen
  after 11.0 s instead of 12.9 s, a new world after 43 s instead of 63 s, before the optimization mods).
* **Fewer WebGL calls**: the WebGL emulation keeps the state it last set (texture parameters per texture, pixel
  store, buffer and texture bindings, attribute pointers and values) and each fixed-function program remembers
  its uniform values: in a singleplayer world, WebGL calls per frame fell from about 9,000 to about 1,450
  (texParameteri 2,981 to 9, pixelStorei 2,911 to 135, mostly from animated textures). Sodium then brought it
  to about 900 (see Optimization mods); the performance edition's multi-draw path about halves the calls again.
* **Lighter video settings** (`WebDefaults`): once per player, options still at vanilla's default become render
  distance 6, biome blend 3×3, mipmap levels 2, fast clouds and decreased particles; options a player changed
  are kept. Graphics offers Fast and Fancy; Fabulous! is not offered (its post-processing chain rendered worlds
  black), an old Fabulous setting loads as Fancy, and the GPU warnlist screen is off (the emulation reports
  OpenGL 2.1, so it always warned).
* **WebGPU** (`?webgpu`, or the loading panel's button; WebGL 2 stays the default): `web/webgpu/webgpu-gl.js`
  implements the WebGL 2 calls the GL emulation makes on WebGPU, so the game itself is unchanged. Shaders are
  converted when a program is linked, GLSL ES 3.00 to Vulkan-style GLSL (`glsl-vk.js`) and then to WGSL by naga,
  the shader translator of wgpu (`web/webgpu/naga`, from the naga-wasm package, MIT/Apache-2.0). OpenGL's
  conventions are kept (clip depth, bottom-up rows in render targets, integer attributes, triangle fans),
  streamed vertices and uniforms go to per-submit buffers, and a frame is submitted when the game swaps
  buffers. Frame rates on the test machine were the same as WebGL within noise (the time goes to Minecraft's own
  code); its use is shader packs (see Iris under "Mods").
