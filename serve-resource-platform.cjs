const http = require('http');
const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, 'frontend', 'dist');
const port = Number(process.env.PORT || 5190);
const types = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif': 'image/gif',
  '.svg': 'image/svg+xml',
  '.ico': 'image/x-icon',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2'
};
function send(res, status, body, type = 'text/plain; charset=utf-8') {
  res.writeHead(status, { 'Content-Type': type, 'Cache-Control': 'no-cache' });
  res.end(body);
}
const server = http.createServer((req, res) => {
  const url = decodeURIComponent((req.url || '/').split('?')[0]);
  let target = path.normalize(path.join(root, url));
  if (!target.startsWith(root)) return send(res, 403, 'Forbidden');
  if (url === '/' || !path.extname(target)) target = path.join(root, 'index.html');
  fs.readFile(target, (err, data) => {
    if (err) {
      fs.readFile(path.join(root, 'index.html'), (fallbackErr, fallback) => {
        if (fallbackErr) return send(res, 404, 'Not found');
        send(res, 200, fallback, types['.html']);
      });
      return;
    }
    send(res, 200, data, types[path.extname(target).toLowerCase()] || 'application/octet-stream');
  });
});
server.listen(port, '127.0.0.1', () => {
  console.log(`Learning Resource Platform static server: http://127.0.0.1:${port}/`);
});
