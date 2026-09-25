/** Небольшой сервер только для локального просмотра прототипов без установки npm-пакетов. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');

const root = __dirname;
const appStaticRoot = path.resolve(__dirname, '..', 'src', 'main', 'resources', 'static');
const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.svg': 'image/svg+xml' };

http.createServer((request, response) => {
  const requested = decodeURIComponent((request.url || '/').split('?')[0]);
  const isAppStatic = requested.startsWith('/src/main/resources/static/');
  const relative = requested === '/'
    ? 'index.html'
    : requested.replace(/^\/+/, '') + (requested.endsWith('/') ? 'index.html' : '');
  const file = isAppStatic
    ? path.resolve(appStaticRoot, requested.slice('/src/main/resources/static/'.length))
    : path.resolve(root, relative);

  // Кроме файлов самих прототипов сервер отдаёт только read-only ассеты текущего приложения.
  // Это позволяет макетам использовать реальные иконки и учебные иллюстрации без их копирования.
  const insidePrototype = file.startsWith(root + path.sep) || file === path.join(root, 'index.html');
  const insideAppStatic = isAppStatic && file.startsWith(appStaticRoot + path.sep);
  if (!insidePrototype && !insideAppStatic) {
    response.writeHead(403).end('Forbidden');
    return;
  }
  fs.readFile(file, (error, data) => {
    if (error) {
      response.writeHead(404).end('Not found');
      return;
    }
    response.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
    response.end(data);
  });
}).listen(4173, '127.0.0.1', () => console.log('DamuLab redesign: http://127.0.0.1:4173'));
