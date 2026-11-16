#!/bin/bash
# Downloads the mod set the browser build is tested with into ./mods, from Modrinth, checking each file
# against the SHA-512 Modrinth lists:
#  - the mods of Fabulously Optimized 1.12.3 (its last pack for Minecraft 1.16.5), except Fabric API (the build
#    already includes it), FerriteCore 2.1.0 (2.1.1 is used) and Hydrogen (optional in the pack, which turns it off
#    on Java 16 and later; it generates classes at run time);
#  - FerriteCore 2.1.1;
#  - UI and helper mods: REI, AppleSkin, WTHIT, Mouse Wheelie, BetterF3, ShulkerBoxTooltip.
# Light Overlay is left out: it calls its own Fabric code through MethodHandles looked up by names built at
# run time, which a compiled build cannot provide.
# Map mods (Xaero's, JourneyMap) are left out: their licences forbid redistributing modified copies, which a
# compiled build is.
#
#   scripts/get-mods.sh            # then scripts/build-web.sh
#   scripts/get-mods.sh perf       # only the performance mods: Sodium, Lithium, Phosphor, FerriteCore (perf branch)
# MODS_DIR=... puts them into another folder (scripts/build-web.sh reads the same variable).
set -euo pipefail
cd "$(dirname "$0")/.."
export MODS_DIR=${MODS_DIR:-mods} GET_MODS_SET=${1:-all}
mkdir -p "$MODS_DIR" .cache/mods
python3 - <<'EOF'
import hashlib, io, json, os, urllib.parse, urllib.request, zipfile

API = 'https://api.modrinth.com/v2'
FO_VERSION = '1.12.3'
EXCLUDE = ('fabric-api-', 'ferritecore-2.1.0-', 'hydrogen-')
MODS = os.environ['MODS_DIR']
PERF_ONLY = os.environ.get('GET_MODS_SET') == 'perf'
PERF = ('sodium-fabric-', 'lithium-fabric-', 'phosphor-fabric-')  # from the pack; FerriteCore 2.1.1 below
EXTRA = [  # slug, version number (exact, so builds are reproducible)
    ('ferrite-core', '2.1.1-fabric'),
    ('rei', '5.12.385+fabric'),
    ('appleskin', '2.5.1+mc1.16.5'),
    ('wthit', 'fabric-2.10.2'),
    ('mouse-wheelie', '1.13.0+mc1.16.5'),
    ('betterf3', '1.5.0'),
    ('shulkerboxtooltip', '2.3.9+1.16.5'),
]


def get(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'teafabric-get-mods'})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def fetch(name, url, sha512):
    dest = os.path.join(MODS, name)
    if os.path.exists(dest) and hashlib.sha512(open(dest, 'rb').read()).hexdigest() == sha512:
        return 'present'
    data = get(url)
    if hashlib.sha512(data).hexdigest() != sha512:
        raise SystemExit('checksum mismatch: ' + name)
    open(dest, 'wb').write(data)
    return 'downloaded'


q = '?game_versions=' + urllib.parse.quote('["1.16.5"]')
fo = [v for v in json.loads(get(API + '/project/fabulously-optimized/version' + q)) if v['version_number'] == FO_VERSION][0]
pack = zipfile.ZipFile(io.BytesIO(get(fo['files'][0]['url'])))
index = json.loads(pack.read('modrinth.index.json'))
n = 0
for f in index['files']:
    name = os.path.basename(f['path'])
    if name.startswith(EXCLUDE) or (PERF_ONLY and not name.startswith(PERF)):
        continue
    print('%-55s %s' % (name, fetch(name, f['downloads'][0], f['hashes']['sha512'])))
    n += 1
for slug, number in (EXTRA[:1] if PERF_ONLY else EXTRA):
    v = [v for v in json.loads(get(API + '/project/%s/version' % slug)) if v['version_number'] == number
         and 'fabric' in v['loaders'] and '1.16.5' in v['game_versions']][0]
    f = next((x for x in v['files'] if x.get('primary')), v['files'][0])
    print('%-55s %s' % (f['filename'], fetch(f['filename'], f['url'], f['hashes']['sha512'])))
    n += 1
print('%d mods in %s' % (n, MODS))
EOF
