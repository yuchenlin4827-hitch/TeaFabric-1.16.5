#!/bin/bash
# One command from a fresh checkout (or after changing ./mods) to a runnable page in build/web:
#   download Minecraft + Fabric (first run), bake the mods with Fabric Loader/Mixin, compile with TeaVM
#   (generating stubs for browser-impossible APIs until nothing is missing) and package.
#
#   scripts/build-web.sh                 # game + Fabric API + every jar in ./mods
#   WITH_FABRIC_API=false scripts/build-web.sh
#   TEAVM_OPTS="-PteavmOpt=ADVANCED" scripts/build-web.sh
#
# Then: python3 -m http.server -d build/web 8080
set -u
cd "$(dirname "$0")/.."
GRADLE=${GRADLE:-gradle}
[ -x ./gradlew ] && GRADLE=./gradlew
TEAVM_OPTS=${TEAVM_OPTS:--PteavmOpt=SIMPLE -PteavmObfuscated=false}

step() { echo; echo "==> $*"; }
run() { "$GRADLE" --console=plain -q "$@"; }

if [ ! -f .cache/setup.properties ]; then
  step "Downloading Minecraft 1.16.5 and Fabric"
  run :tools:setupMinecraft || exit 1
fi
if [ ! -f .cache/minecraft/client-intermediary.jar ]; then
  step "Remapping the client to intermediary names"
  run :tools:remapIntermediary || exit 1
fi

step "Baking mods (Fabric Loader + Mixin): $(ls mods/*.jar 2>/dev/null | wc -l) jar(s) in ./mods"
run bake -PwithFabricApi="${WITH_FABRIC_API:-true}" || exit 1

compiled=0
for attempt in 1 2 3 4 5 6; do
  step "Compiling to WebAssembly (attempt $attempt)"
  # shellcheck disable=SC2086
  if run compileWeb $TEAVM_OPTS; then
    compiled=1
    break
  fi
  missing=$(cat build/teavm/wasm-gc/missing.tsv 2>/dev/null | grep -c .)
  if [ "$missing" -eq 0 ]; then
    echo "compileWeb failed for a reason other than missing APIs; see the output above." >&2
    exit 1
  fi
  step "Generating stubs for $missing missing API(s)"
  run stubWeb || exit 1
done
[ "$compiled" -eq 1 ] || { echo "Still missing APIs after several attempts: build/teavm/wasm-gc/missing.txt" >&2; exit 1; }

step "Packaging build/web"
run packageWeb || exit 1
echo
echo "Done. Serve it with:  python3 -m http.server -d build/web 8080"
