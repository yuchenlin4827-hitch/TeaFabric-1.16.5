// Renders deploy/cloudflare/download.html for /download/.
// usage: node render-download.js <template> <manifest.json> [server address]
const fs = require('fs');
const [template, manifestFile, server] = process.argv.slice(2);
const m = JSON.parse(fs.readFileSync(manifestFile, 'utf8'));
const escape = (s) => String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
const serverBlock = server
  ? `    <div class="address"><code>${escape(server)}</code><button type="button" data-copy="${escape(server)}">複製</button></div>\n` +
    '    <p>在「多人遊戲」→「新增伺服器」填入上面的地址。</p>'
  : '    <p>在「多人遊戲」→「新增伺服器」填入服主給你的地址。</p>';
const values = { NAME: m.name, SIZE: (m.size / 1048576).toFixed(0) + ' MB', SHA256: m.sha256 };
let html = fs.readFileSync(template, 'utf8').replace('{{SERVER_BLOCK}}', serverBlock);
html = html.replace(/\{\{(\w+)\}\}/g, (match, key) => (key in values ? escape(values[key]) : match));
process.stdout.write(html);
