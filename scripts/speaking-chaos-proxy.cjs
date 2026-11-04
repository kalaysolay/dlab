// Локальная QA-проверка потерянного HTTP-ответа. Не запускать перед production-прокси.
// Только loopback; ни заголовки, ни тела запросов/ответов не записываются.
const http=require('node:http');
function port(name,fallback){const i=process.argv.indexOf(name),value=i<0?fallback:Number(process.argv[i+1]);if(!Number.isInteger(value)||value<1024||value>65535)throw Error('Invalid QA port');return value;}
const listen=port('--port',18086),upstream=port('--upstream-port',18084);
let drop=process.argv.includes('--drop-turn-response-once');
const server=http.createServer((request,response)=>{
    const target=http.request({hostname:'127.0.0.1',port:upstream,path:request.url,method:request.method,headers:request.headers},incoming=>{
        const path=new URL(request.url,'http://127.0.0.1').pathname;
        if(drop&&request.method==='POST'&&/\/api\/student\/speaking\/sessions\/[^/]+\/turns$/.test(path)&&incoming.statusCode===201){
            drop=false;incoming.resume();incoming.once('end',()=>{response.destroy();console.log('QA: dropped one successful turn response.');});return;
        }
        response.writeHead(incoming.statusCode,incoming.headers);incoming.pipe(response);
    });
    target.on('error',()=>{if(!response.headersSent)response.writeHead(503,{'Cache-Control':'no-store'});response.end();});
    request.on('aborted',()=>target.destroy());request.pipe(target);
});
server.listen(listen,'127.0.0.1',()=>console.log(`QA proxy on loopback:${listen}, upstream loopback:${upstream}.`));
process.on('SIGINT',()=>server.close(()=>process.exit(0)));
