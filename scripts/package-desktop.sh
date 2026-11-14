#!/bin/bash
# Packages build/web as a download: build/TeaFabric-1.16.5.zip with the desktop launcher (launcher/,
# a small Go program that serves ./web on http://127.0.0.1:47265/ and opens the browser) for Windows,
# macOS and Linux. Run scripts/build-web.sh first. Needs Go.
#
#   scripts/package-desktop.sh
#   SERVER=wss://relay.example.com/ scripts/package-desktop.sh   # server address in the usage guide
#   SOUNDS=all scripts/package-desktop.sh    # with music and music discs (+176 MB); SOUNDS=none: silent
#
# The zip contains Minecraft's code and assets: do not publish it (scripts/deploy-cloudflare.sh serves it
# behind the site password).
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
NAME=TeaFabric-1.16.5
STAGE=$ROOT/build/desktop/$NAME
ZIP=$ROOT/build/$NAME.zip
[ -f build/web/teavm/classes.wasm ] || { echo "build/web is missing: run scripts/build-web.sh first" >&2; exit 1; }
command -v go >/dev/null || { echo "Go is needed to build the launcher" >&2; exit 1; }

rm -rf "$ROOT/build/desktop" "$ZIP"
mkdir -p "$STAGE/web/teavm"
# The page, the runtime and the data archives; not the test pages (build/web/mini*).
cp build/web/index.html build/web/boot.js build/web/style.css "$STAGE/web/"
cp -r build/web/data "$STAGE/web/"
cp build/web/teavm/* "$STAGE/web/teavm/"
# Every language (47 MB, about 12 MB zipped); the page loads the chosen one.
[ -d build/web/lang ] && cp -r build/web/lang "$STAGE/web/"
# Sound effects (default), or all sounds, from build/web/sounds; the page fetches them when played.
sounds=${SOUNDS:-effects}
if [ "$sounds" != none ] && [ -d build/web/sounds ]; then
  mkdir -p "$STAGE/web/sounds"
  index=$(grep '^minecraft.assetIndex=' .cache/setup.properties | cut -d= -f2-)
  node -e '
    const fs = require("fs"), path = require("path");
    const [index, from, to, mode] = process.argv.slice(1);
    let n = 0;
    for (const [name, o] of Object.entries(JSON.parse(fs.readFileSync(index, "utf8")).objects)) {
      if (!name.endsWith(".ogg")) continue;
      if (mode !== "all" && /^minecraft\/sounds\/(music|records)\//.test(name)) continue;
      const file = o.hash + ".ogg";
      if (fs.existsSync(path.join(from, file)) && !fs.existsSync(path.join(to, file))) {
        fs.copyFileSync(path.join(from, file), path.join(to, file));
        n++;
      }
    }
    console.log(n + " sound files (" + mode + ")");
  ' "$index" build/web/sounds "$STAGE/web/sounds" "$sounds"
fi

build() { # GOOS GOARCH output
  (cd launcher && CGO_ENABLED=0 GOOS=$1 GOARCH=$2 go build -trimpath -ldflags "-s -w" -o "$STAGE/$3" .)
}
build windows amd64 TeaFabric.exe
build darwin arm64 TeaFabric-mac-apple-silicon
build darwin amd64 TeaFabric-mac-intel
build linux amd64 TeaFabric-linux
chmod +x "$STAGE"/TeaFabric-*

server_line="向服主索取伺服器地址。"
[ -n "${SERVER:-}" ] && server_line="伺服器地址：$SERVER"
cat > "$STAGE/使用說明.txt" <<EOF
TeaFabric 1.16.5（下載版）

先把整個壓縮檔解壓縮，再從解壓出來的資料夾啟動（web 資料夾要和程式放在一起）。

啟動
- Windows：雙擊 TeaFabric.exe。第一次如果出現「Windows 已保護您的電腦」，
  按「其他資訊」→「仍要執行」。
- macOS：Apple 晶片（M1 以後）用 TeaFabric-mac-apple-silicon，Intel 用 TeaFabric-mac-intel。
  第一次要在 Finder 對它按右鍵 →「打開」→「打開」。
- Linux：在終端機執行 ./TeaFabric-linux

程式會在本機開一個小伺服器（http://127.0.0.1:47265/），並用預設瀏覽器打開遊戲。
玩的時候不要關閉它的視窗；關閉它遊戲就會停止。

瀏覽器
需要 Chrome / Edge 119 以上、Firefox 120 以上或 Safari 18.2 以上。
預設瀏覽器太舊的話，把上面的網址複製到新版瀏覽器打開。

存檔
世界和設定存在瀏覽器裡，跟網站版的存檔分開。
要搬世界：在一邊的世界選單按「編輯」→「打開世界資料夾」會下載成 zip，
再把 zip 拖到另一邊的「選擇世界」畫面。

音效
已附帶音效。背景音樂和唱片不在下載版裡（太大），只有網站版有。

多人遊戲
$server_line
第一次進入時輸入 /register 密碼 密碼 註冊；之後每次進入輸入 /login 密碼。
EOF

(cd "$ROOT/build/desktop" && zip -qr -9 "$ZIP" "$NAME")
echo "$ZIP ($(du -h "$ZIP" | cut -f1))"
