// Изолированная проверка cache-policy без браузера, сети и персональных данных.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const handlers={},writes=[],matches=[],removed=[];
let offline=false;
const sandbox={URL,Response,Promise,console,
    self:{location:{origin:'https://qa.invalid'},addEventListener:(name,fn)=>{handlers[name]=fn;},skipWaiting:async()=>{}},
    clients:{claim:async()=>{},matchAll:async()=>[]},
    fetch:async()=>{if(offline)throw Error('offline');return new Response('public asset');},
    caches:{open:async()=>({addAll:async()=>{},put:async(req)=>{writes.push(req.url);}}),
        match:async key=>{matches.push(key);return new Response('offline shell');},keys:async()=>['old-shell','damulab-shell-v6'],delete:async key=>{removed.push(key);}}
};
sandbox.self.clients=sandbox.clients;
vm.runInNewContext(fs.readFileSync('src/main/resources/static/service-worker.js','utf8'),sandbox);
async function request(path,method='GET',mode='cors'){
    let pending;handlers.fetch({request:{url:'https://qa.invalid'+path,method,mode},respondWith:value=>{pending=value;}});
    return pending?await pending:null;
}
(async()=>{
    for(const path of ['/app','/login','/student/speaking','/api/student/speaking/sessions','/api/admin/speaking/operations']){
        await request(path);assert.equal(writes.length,0);
        offline=true;const response=await request(path);assert.equal(response.status,503);assert.equal(matches.length,0);offline=false;
    }
    offline=true;await request('/student/speaking','GET','navigate');assert.deepEqual(matches,['/offline']);offline=false;
    assert.equal(await request('/api/student/speaking/sessions/id/speech','POST'),null);
    await request('/css/speaking.css');assert.deepEqual(writes,['https://qa.invalid/css/speaking.css']);
    let activation;handlers.activate({waitUntil:value=>{activation=value;}});await activation;
    assert.deepEqual(removed,['old-shell']);
    console.log('Speaking service worker: private GET/POST, offline fallback, public assets and cache activation passed.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
