// Renders deploy/cloudflare/download.html for /download/.
// usage: node render-download.js <template> <hmcl info.json> <download dir> [server address]
const fs = require('fs');
const path = require('path');
const [template, infoFile, dir, server] = process.argv.slice(2);
const info = JSON.parse(fs.readFileSync(infoFile, 'utf8'));
const escape = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const size = (file) => (fs.statSync(path.join(dir, file)).size / 1048576).toFixed(1) + ' MB';
const exe = path.basename(info.exe);
const jar = path.basename(info.jar);
const serverBlock = server
  ? `    <div class="address"><code>${escape(server)}</code><button type="button" data-copy="${escape(server)}">複製</button></div>`
  : '    <p>請向服主索取伺服器地址。</p>';
const values = {
  VERSION: info.version,
  EXE_FILE: exe,
  EXE_SIZE: size(exe),
  EXE_SHA256: info.exesha256,
  JAR_FILE: jar,
  JAR_SIZE: size(jar),
  JAR_SHA256: info.jarsha256,
};
let html = fs.readFileSync(template, 'utf8').replace('{{SERVER_BLOCK}}', serverBlock);
html = html.replace(/\{\{(\w+)\}\}/g, (m, key) => (key in values ? escape(values[key]) : m));
process.stdout.write(html);
