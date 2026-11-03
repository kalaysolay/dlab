/* Локальный просмотр всех макетов redesign и реальных ассетов DamuLab без зависимостей. */
const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '..');
const staticRoot = path.resolve(root, '../src/main/resources/static');
const types = {'.html':'text/html; charset=utf-8','.css':'text/css; charset=utf-8','.js':'text/javascript; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ttf':'font/ttf','.md':'text/plain; charset=utf-8'};
const port = Number(process.env.PORT || 4184);
http.createServer((req,res)=>{
  let pathname;
  try { pathname=decodeURIComponent(new URL(req.url,'http://localhost').pathname); }
  catch { res.writeHead(400).end('Bad request');return; }
  if(pathname==='/'){res.writeHead(302,{Location:'/english-speaking/'}).end();return;}
  const appPrefix='/src/main/resources/static/';
  const asset=pathname.startsWith(appPrefix);
  const base=asset?staticRoot:root;
  const relative=asset?pathname.slice(appPrefix.length):pathname.replace(/^\/+/, '');
  const file=path.resolve(base,relative+(pathname.endsWith('/')?'index.html':''));
  // Ограничиваем раздачу макетами и static; исходники основного приложения и .env находятся вне этих каталогов.
  if(!file.startsWith(base+path.sep)){res.writeHead(403).end('Forbidden');return;}
  fs.readFile(file,(err,data)=>{
    if(err){res.writeHead(404).end('Not found');return;}
    res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store'}).end(data);
  });
}).listen(port,'127.0.0.1',()=>console.log(`DamuLab speaking mockup: http://127.0.0.1:${port}/english-speaking/`));
