// Регрессии жизненного цикла PWA и валидации маршрута без сети/биометрического устройства.
const fs=require('node:fs'),vm=require('node:vm'),assert=require('node:assert/strict');
const source=fs.readFileSync('src/main/resources/static/js/app-entry.js','utf8');
const resumeSource=fs.readFileSync('src/main/resources/static/js/pwa-resume.js','utf8');
const session='/student/speaking?lang=ru#session/405c3628-d5c8-46d5-855c-45fc679564c3';
function page(path,{standalone=true,saved=null,home=null,blocked=false}={}){
    const url=new URL(path,'https://qa.invalid'),events={},navigations=[],stored=new Map(saved?[['damulab-pwa-last-route',saved]]:[]);
    let launch;
    const location={origin:url.origin,pathname:url.pathname,search:url.search,hash:url.hash,
        replace:value=>navigations.push(value),assign:value=>navigations.push(value)};
    const document={visibilityState:'visible',documentElement:{classList:{add:()=>{throw Error('Unexpected hiding');}}},
        addEventListener:(name,fn)=>{events[name]=fn;},querySelector:()=>home?{dataset:{pwaEntryHome:home},querySelector:()=>null}:null};
    const window={location,matchMedia:()=>({matches:standalone}),addEventListener:(name,fn)=>{events[name]=fn;},
        launchQueue:{setConsumer:fn=>{launch=fn;}}};
    const storage={getItem:k=>{if(blocked)throw Error('denied');return stored.get(k)||null;},setItem:(k,v)=>{if(blocked)throw Error('denied');stored.set(k,v);},removeItem:k=>stored.delete(k)};
    const sandbox={window,document,navigator:{standalone},sessionStorage:storage,localStorage:{getItem:()=> '1'},URL};vm.runInNewContext(source,sandbox);
    return {window,document,events,navigations,stored,resume:()=>vm.runInNewContext(resumeSource,sandbox),launch:params=>launch?.(params)};
}
for(const path of [session,'/student/lectures/12','/student/quiz/rooms/ABC123','/parent','/admin/questions']){
    const p=page(path);
    p.document.visibilityState='hidden';p.events.visibilitychange?.();p.events.pagehide?.();
    p.document.visibilityState='visible';p.events.visibilitychange?.();p.events.pageshow?.({persisted:true});
    assert.deepEqual(p.navigations,[],path+' must retain its document');
    p.launch({targetURL:'https://qa.invalid/app'});assert.deepEqual(p.navigations,[]);
}
const resumed=page('/app',{home:'/student',saved:session});resumed.resume();assert.deepEqual(resumed.navigations,[session]);
for(const saved of ['//evil.invalid/student','https://evil.invalid/student','/parent/private','/api/student/data','javascript:alert(1)','/login?token=secret']){
    const p=page('/app',{home:'/student',saved});p.resume();assert.deepEqual(p.navigations,['/student']);
}
const anonymous=page('/app',{saved:session});anonymous.resume();assert.deepEqual(anonymous.navigations,[]);
const desktop=page('/app',{home:'/student',saved:session,standalone:false});desktop.resume();assert.deepEqual(desktop.navigations,['/student']);
const blocked=page('/app',{home:'/student',saved:session,blocked:true});blocked.resume();assert.deepEqual(blocked.navigations,['/student']);
const stale=page('/app',{home:'/student',saved:session});delete stale.window.damulabPwaEntryTarget;stale.resume();assert.deepEqual(stale.navigations,['/student']);
const broken=page('/app',{home:'/student',saved:session});broken.window.damulabPwaEntryTarget=()=>{throw Error('old script');};broken.resume();assert.deepEqual(broken.navigations,['/student']);
const links=page(session);links.launch({targetURL:'https://evil.invalid/student'});assert.deepEqual(links.navigations,[]);
links.launch({targetURL:'https://qa.invalid/student/lectures/42'});assert.deepEqual(links.navigations,['/student/lectures/42']);
links.launch({targetURL:'https://qa.invalid/student/tests?topic=10'});assert.equal(links.navigations.at(-1),'/student/tests?topic=10');
links.launch({targetURL:'https://qa.invalid/activate-account?token=test-token'});assert.equal(links.navigations.at(-1),'/activate-account?token=test-token');
const sensitive=page('/student/tests?email=private&token=secret&lang=kk#token=secret');assert.equal([...sensitive.stored.values()][0],'/student/tests?lang=kk');
const logout=page(session);logout.events.submit({target:{action:'https://qa.invalid/logout'}});logout.events.pagehide();assert.equal(logout.stored.size,0);
const manifest=JSON.parse(fs.readFileSync('src/main/resources/static/manifest.webmanifest','utf8'));assert.equal(manifest.launch_handler.client_mode,'focus-existing');
console.log('PWA resume: background/BFCache, icon/deep links, session route, roles, expired entry and blocked storage passed.');
