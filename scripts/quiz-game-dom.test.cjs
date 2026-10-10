/* Настоящий DOM клиента с fake HTTP: нет браузерной/рабочей БД, проверяются отправка, фокус, polling и безопасный текст. */
const {test}=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path');
const {JSDOM}=require('./quiz-ui/node_modules/jsdom');
const html=fs.readFileSync(path.join(__dirname,'../src/main/resources/templates/student/quiz-game-room.html'),'utf8');
const core=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/quiz-game-core.js'),'utf8'), client=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/quiz-games.js'),'utf8');
const flush=async()=>{for(let i=0;i<4;i++) await new Promise(setImmediate);};
async function harness(format='SCQ',kind='QUESTION') {
    const dom=new JSDOM(html,{url:'https://quiz.test/student/quiz/rooms/QZABCD',runScripts:'outside-only'}), w=dom.window;
    const root=w.document.querySelector('[data-quiz-game-room]'); root.dataset.code='QZABCD';root.dataset.initialStatus='active';
    for(const [name,value] of [['_csrf','test-csrf'],['_csrf_header','X-CSRF-TOKEN']]){const meta=w.document.createElement('meta');meta.name=name;meta.content=value;w.document.head.append(meta);}
    w.quizLabels=new Proxy({threshold:'Осталось {n}',true:'Верю',false:'Не верю',accepted:'Ответ принят',uncertain:'Не подтверждено'}, {get:(target,key)=>target[key]||key});
    const now=Date.now(); w.Date.now=()=>now;
    const options=format==='MATCHING'?{left:[{id:'1',text:'Один'}],right:[{id:'a',text:'Первый'}]}:format==='FILL_IN'?{placeholders:['[[1]]']}:[{id:'A',text:'<script>вариант</script>'},{id:'B',text:'Второй'}];
    let state={code:'QZABCD',status:'active',gameType:kind==='FACT'?'TRUE_FALSE':kind==='VOCABULARY'?'WORD_TRANSLATION':'SUBJECT_QUIZ',questionCount:2,serverTime:new Date(now).toISOString(),
        currentRound:{id:1,orderNo:1,contentKind:kind,format,prompt:'<script>условие</script>',options,locale:'ru',presentation:{},startsAt:new Date(now-1000).toISOString(),endsAt:new Date(now+20000).toISOString(),answered:false}};
    const requests=[], intervals=[];let fail=false;
    w.setInterval=fn=>{intervals.push(fn);return intervals.length;};w.clearInterval=()=>{};w.setTimeout=()=>1;w.clearTimeout=()=>{};w.WebSocket=undefined;
    w.fetch=async(url,init)=>{
        if(init.method==='POST') {requests.push({url,payload:JSON.parse(init.body),headers:init.headers});if(fail)throw new Error('offline');state={...state,currentRound:{...state.currentRound,answered:true}};}
        return {ok:true,status:200,json:async()=>structuredClone(state)};
    };
    w.eval(core);w.eval(client);await flush();
    return {w,dom,requests,intervals,setState:s=>{state=s;},getState:()=>state,setFail:b=>{fail=b;},close:()=>{w.dispatchEvent(new w.Event('pagehide'));w.close();}};
}
for(const [format,kind] of [['SCQ','QUESTION'],['MCQ','QUESTION'],['MATCHING','QUESTION'],['FILL_IN','QUESTION'],['SCQ','VOCABULARY'],['BOOLEAN','FACT']]) {
    test(`DOM ${kind}/${format}: typed ответ, окончательная блокировка и фокус статуса`,async()=>{
        const h=await harness(format,kind), d=h.w.document;
        try {
            assert.equal(d.activeElement.id,'qg-question'); assert.equal(d.querySelector('#qg-options script'),null);
            if(kind==='FACT')d.querySelector('input[value="false"]').checked=true;
            else if(format==='MATCHING')d.querySelector('[data-answer-key]').value='a';
            else if(format==='FILL_IN')d.querySelector('[data-answer-key]').value='30';
            else d.querySelector('input[value="B"]').checked=true;
            d.querySelector('#qg-answer-form').dispatchEvent(new h.w.Event('submit',{cancelable:true}));await flush();
            assert.equal(h.requests.length,1);assert.equal(h.requests[0].headers['X-CSRF-TOKEN'],'test-csrf');
            assert.equal(d.querySelector('#qg-answer-fields').disabled,true);assert.equal(d.activeElement.id,'qg-status');assert.equal(d.querySelector('#qg-status').textContent,'Ответ принят');
            if(kind==='FACT')assert.equal(h.requests[0].payload.answer,false);
            else if(format==='MATCHING')assert.deepEqual(h.requests[0].payload.answer,{pairs:{'1':'a'}});
            else if(format==='FILL_IN')assert.deepEqual(h.requests[0].payload.answer,{answers:{'[[1]]':'30'}});
            else assert.deepEqual(h.requests[0].payload.answer,{selected:['B']});
            d.querySelector('#qg-skip').click();await flush();assert.equal(h.requests.length,1);
        }finally{h.close();}
    });
}
test('явный skip не включает answer и не показывает правильность',async()=>{
    const h=await harness();try{h.w.document.querySelector('#qg-skip').click();await flush();assert.deepEqual(h.requests[0].payload,{roundId:1,skipped:true});assert.equal(h.w.document.querySelector('#qg-review').textContent,'');}finally{h.close();}
});
test('poll сохраняет выбор того же раунда и фокусирует заголовок нового',async()=>{
    const h=await harness();try{
        const d=h.w.document;d.querySelector('input[value="B"]').checked=true;await h.intervals[0]();await flush();assert.equal(d.querySelector('input[value="B"]').checked,true);
        h.setState({...h.getState(),currentRound:{...h.getState().currentRound,id:2,orderNo:2,prompt:'Следующий'}});await h.intervals[0]();await flush();
        assert.equal(d.activeElement.id,'qg-question');assert.equal(d.querySelector('input[value="B"]').checked,false);
    }finally{h.close();}
});
test('сетевой отказ не обещает сохранение и повторяет тот же payload',async()=>{
    const h=await harness();try{const d=h.w.document;d.querySelector('input[value="B"]').checked=true;h.setFail(true);d.querySelector('#qg-answer-form').dispatchEvent(new h.w.Event('submit',{cancelable:true}));await flush();
        assert.equal(d.querySelector('#qg-status').textContent,'Не подтверждено');assert.equal(d.querySelector('#qg-answer-fields').disabled,true);
        h.setFail(false);d.querySelector('#qg-answer-form').dispatchEvent(new h.w.Event('submit',{cancelable:true}));await flush();assert.deepEqual(h.requests[0].payload,h.requests[1].payload);
    }finally{h.close();}
});
test('истёкшее окно блокирует ввод; клиент не отправляет автоматический answer',async()=>{
    const h=await harness();try{h.setState({...h.getState(),currentRound:{...h.getState().currentRound,endsAt:new Date(Date.now()-1000).toISOString()}});await h.intervals[0]();await flush();
        assert.equal(h.w.document.querySelector('#qg-answer-fields').disabled,true);assert.equal(h.requests.length,0);
    }finally{h.close();}
});

test('host: приглашение открывается, poll сохраняет ввод до новой revision',async()=>{
    const h=await harness();try{
        const d=h.w.document;
        const lobby={...h.getState(),status:'waiting',currentRound:null,currentParticipantHost:true,canStart:true,canInvite:true,
            settingsRevision:0,gradeSelectionMode:'NONE',questionCountEditable:true,roundSecondsEditable:true,roundSeconds:30,
            minQuestionCount:1,maxQuestionCount:10,minRoundSeconds:10,maxRoundSeconds:120,startBlockers:[],
            participants:[{displayName:'Host',current:true,host:true,ready:true}]};
        h.setState(lobby);await h.intervals[0]();await flush();
        assert.equal(d.querySelector('#qg-start').hidden,false);assert.equal(d.querySelector('#qg-ready').hidden,true);
        d.querySelector('#qg-invite').click();assert.equal(d.querySelector('#qg-invitation').hidden,false);
        d.querySelector('#qg-invite').click();assert.equal(d.querySelector('#qg-invitation').hidden,true);
        d.querySelector('#qg-count').value='7';d.querySelector('#qg-count').dispatchEvent(new h.w.Event('input',{bubbles:true}));
        await h.intervals[0]();await flush();assert.equal(d.querySelector('#qg-count').value,'7');
        h.setState({...lobby,settingsRevision:1,questionCount:3});await h.intervals[0]();await flush();
        assert.equal(d.querySelector('#qg-count').value,'3');
    }finally{h.close();}
});

for(const terminal of ['cancelled','expired'])test(`${terminal}: фокус на завершении и отсутствие игровых действий`,async()=>{
    const h=await harness();try{
        h.setState({...h.getState(),status:terminal,currentRound:null});await h.intervals[0]();await flush();
        const d=h.w.document;assert.equal(d.activeElement.id,'qg-terminal-title');
        assert.equal(d.querySelector('#qg-terminal').hidden,false);assert.equal(d.querySelector('#qg-active').hidden,true);
        assert.equal(d.querySelector('#qg-lobby').hidden,true);assert.equal(h.requests.length,0);
    }finally{h.close();}
});
