#!/bin/bash
# Deploys build/web to Cloudflare Workers, behind a password (the page contains Minecraft's code and
# assets, which must not be published). Run scripts/build-web.sh first.
#
#   scripts/deploy-cloudflare.sh              # deploy; on the first run a password is generated and printed
#   WORKER_NAME=my-mc scripts/deploy-cloudflare.sh
#   PASSWORD=... scripts/deploy-cloudflare.sh # set or change the password
#
# Needs Node.js and a Cloudflare account (npx wrangler login, or CLOUDFLARE_API_TOKEN).
# classes.wasm is over Cloudflare's 25 MiB file limit, so it is uploaded in parts that boot.js reassembles.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$PWD
OUT=$ROOT/.cache/cloudflare
WRANGLER=${WRANGLER:-npx --yes wrangler@4}
[ -f build/web/teavm/classes.wasm ] || { echo "build/web is missing: run scripts/build-web.sh first" >&2; exit 1; }

rm -rf "$OUT/dist"
mkdir -p "$OUT/dist"
# The page, the runtime, the data archives; not the test pages (build/web/mini*).
cp build/web/index.html build/web/boot.js build/web/style.css "$OUT/dist/"
cp -r build/web/data "$OUT/dist/"
mkdir -p "$OUT/dist/teavm"
for f in build/web/teavm/*; do
  [ "$(basename "$f")" = classes.wasm ] || cp "$f" "$OUT/dist/teavm/"
done
split -b 24m -d -a 2 build/web/teavm/classes.wasm "$OUT/dist/teavm/classes.wasm.part"
parts=$(cd "$OUT/dist/teavm" && ls classes.wasm.part* | sed 's/.*/"&"/' | paste -sd, -)
echo "{\"parts\": [$parts]}" > "$OUT/dist/teavm/classes.wasm.parts.json"
for f in $(find "$OUT/dist" -type f -size +25M); do
  echo "$f is over Cloudflare's 25 MiB limit" >&2; exit 1
done

name=${WORKER_NAME:-teafabric}
sed "s/\"name\": \"teafabric\"/\"name\": \"$name\"/" deploy/cloudflare/wrangler.jsonc > "$OUT/wrangler.jsonc"
cp deploy/cloudflare/worker.js "$OUT/"
cd "$OUT"
$WRANGLER deploy

# The Worker refuses every request until it has a password.
if [ -n "${PASSWORD:-}" ] || ! $WRANGLER secret list 2>/dev/null | grep -q AUTH_PASSWORD; then
  password=${PASSWORD:-$(node -e "console.log(require('crypto').randomBytes(12).toString('base64url'))")}
  printf '%s' "$password" | $WRANGLER secret put AUTH_PASSWORD
  echo
  echo "Password (any user name): $password"
fi
