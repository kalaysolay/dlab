/* Настоящий DOM клиента с fake HTTP: нет браузерной/рабочей БД, проверяются отправка, фокус, polling и безопасный текст. */
const {test}=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path');
const {JSDOM}=require('./quiz-ui/node_modules/jsdom');
const html=fs.readFileSync(path.join(__dirname,'../src/main/resources/templates/student/quiz-game-room.html'),'utf8');
const core=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/quiz-game-core.js'),'utf8'), client=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/quiz-games.js'),'utf8');
const flush=async()=>{for(let i=0;i<4;i++) await new Promise(setImmediate);};
async function lobbyHarness() {
    const h=await harness('BOOLEAN','FACT');
    const lobby={...h.getState(),status:'waiting',currentRound:null,currentParticipantHost:true,canStart:true,
        settingsRevision:0,gradeSelectionMode:'NONE',questionCountEditable:true,roundSecondsEditable:true,
        roundSeconds:20,minQuestionCount:1,maxQuestionCount:10,minRoundSeconds:5,maxRoundSeconds:120,
        startBlockers:[],participants:[{displayName:'Host',current:true,host:true,ready:true}]};
    h.setState(lobby);h.refresh();await flush();return h;
}

test('старт применяет текущие настройки одним кликом и блокирует повторный запрос',async()=>{
    const h=await lobbyHarness();try{
        const d=h.w.document;
        assert.equal(d.querySelector('#qg-settings button[type="submit"]'),null);
        assert.equal(d.querySelector('#qg-lobby a[href="/student/profile"]'),null);
        d.querySelector('#qg-count').value='7';d.querySelector('#qg-seconds').value='15';
        d.querySelector('#qg-count').dispatchEvent(new h.w.Event('input',{bubbles:true}));
        h.setPostHandler((request,state)=>request.url.endsWith('/settings')
            ? {...state,...request.payload,settingsRevision:1}
            : {...state,status:'active',currentRound:null});
        d.querySelector('#qg-start').click();d.querySelector('#qg-start').click();await flush();
        assert.equal(h.requests.length,2);
        assert.ok(h.requests[0].url.endsWith('/settings'));assert.ok(h.requests[1].url.endsWith('/start'));
        assert.deepEqual(h.requests[0].payload,{settingsRevision:0,gradeId:null,questionCount:7,roundSeconds:15});
        assert.equal(h.getState().status,'active');assert.equal(h.getState().questionCount,7);
    }finally{h.close();}
});

test('старт без изменений не сохраняет настройки повторно',async()=>{
    const h=await lobbyHarness();try{
        h.setPostHandler((request,state)=>({...state,status:'active',currentRound:null}));
        h.w.document.querySelector('#qg-start').click();await flush();
        assert.equal(h.requests.length,1);assert.ok(h.requests[0].url.endsWith('/start'));
    }finally{h.close();}
});

test('невалидные поля и ошибка сохранения не запускают игру и сохраняют ввод',async()=>{
    const h=await lobbyHarness();try{
        const d=h.w.document;
        d.querySelector('#qg-count').value='99';d.querySelector('#qg-start').click();await flush();
        assert.equal(h.requests.length,0);
        d.querySelector('#qg-count').value='7';h.setFail(true);
        d.querySelector('#qg-start').click();await flush();
        assert.equal(h.requests.length,1);assert.ok(h.requests[0].url.endsWith('/settings'));
        assert.equal(d.querySelector('#qg-count').value,'7');assert.equal(d.querySelector('#qg-start').disabled,false);
    }finally{h.close();}
});

test('изменённые правила группы ожидают новой готовности участников',async()=>{
    const h=await lobbyHarness();try{
        const d=h.w.document;d.querySelector('#qg-seconds').value='15';
        h.setPostHandler((request,state)=>({...state,...request.payload,settingsRevision:1,canStart:false,startBlockers:['PARTICIPANT_NOT_READY']}));
        d.querySelector('#qg-start').click();await flush();
        assert.equal(h.requests.length,1);assert.ok(h.requests[0].url.endsWith('/settings'));
        assert.equal(d.querySelector('#qg-start').disabled,true);
        assert.equal(d.querySelector('#qg-blockers').textContent,'PARTICIPANT_NOT_READY');
    }finally{h.close();}
});
async function harness(format='SCQ',kind='QUESTION') {
    const dom=new JSDOM(html,{url:'https://quiz.test/student/quiz/rooms/QZABCD',runScripts:'outside-only'}), w=dom.window;
    const root=w.document.querySelector('[data-quiz-game-room]'); root.dataset.code='QZABCD';root.dataset.initialStatus='active';
    for(const [name,value] of [['_csrf','test-csrf'],['_csrf_header','X-CSRF-TOKEN']]){const meta=w.document.createElement('meta');meta.name=name;meta.content=value;w.document.head.append(meta);}
    w.quizLabels=new Proxy({threshold:'Осталось {n}',true:'Верю',false:'Не верю',accepted:'Ответ принят',uncertain:'Не подтверждено'}, {get:(target,key)=>target[key]||key});
    const now=Date.now(); w.Date.now=()=>now;
    const options=format==='MATCHING'?{left:[{id:'1',text:'Один'}],right:[{id:'a',text:'Первый'}]}:format==='FILL_IN'?{placeholders:['[[1]]']}:[{id:'A',text:'<script>вариант</script>'},{id:'B',text:'Второй'}];
    let state={code:'QZABCD',status:'active',gameType:kind==='FACT'?'TRUE_FALSE':kind==='VOCABULARY'?'WORD_TRANSLATION':'SUBJECT_QUIZ',questionCount:2,serverTime:new Date(now).toISOString(),
        currentRound:{id:1,orderNo:1,contentKind:kind,format,prompt:'<script>условие</script>',options,locale:'ru',presentation:{},startsAt:new Date(now-1000).toISOString(),endsAt:new Date(now+20000).toISOString(),answered:false}};
    const requests=[], intervals=[];let fail=false, postHandler=null, results=null;
    w.setInterval=fn=>{intervals.push(fn);return intervals.length;};w.clearInterval=()=>{};w.setTimeout=()=>1;w.clearTimeout=()=>{};w.WebSocket=undefined;
    w.fetch=async(url,init)=>{
        if(url.endsWith('/results')) return {ok:true,status:200,json:async()=>structuredClone(results)};
        if(init.method==='POST') {requests.push({url,payload:JSON.parse(init.body),headers:init.headers});if(fail)throw new Error('offline');if(postHandler)state=await postHandler(requests.at(-1),state);else state={...state,currentRound:{...state.currentRound,answered:true}};}
        return {ok:true,status:200,json:async()=>structuredClone(state)};
    };
    w.eval(core);w.eval(client);await flush();
    return {w,dom,requests,intervals,refresh:()=>w.dispatchEvent(new w.Event('online')),setResults:r=>{results=r;},setPostHandler:fn=>{postHandler=fn;},setState:s=>{state=s;},getState:()=>state,setFail:b=>{fail=b;},close:()=>{w.dispatchEvent(new w.Event('pagehide'));w.close();}};
}
test('разбор: false, безопасный текст, пропуски и фильтры без повторной загрузки результата',async()=>{
    const h=await harness('BOOLEAN','FACT'),d=h.w.document;
    try {
        d.querySelector('[data-quiz-game-room]').dataset.initialStatus='finished';
        const prompt='<img src=x onerror=alert(1)>', explanation='<script>объяснение</script>';
        h.setResults({me:{place:1,score:1,maxScore:4,correctAnswers:1,totalRounds:4,percent:25},leaderboard:[],review:[
            {orderNo:1,prompt,correct:true,answer:false,correctAnswer:false,options:[],explanation,sourceReference:'https://example.test'},
            {orderNo:2,prompt:'Ошибка',correct:false,answer:true,correctAnswer:false,options:[]},
            {orderNo:3,prompt:'Пропуск',correct:false,skipped:true,answer:null,correctAnswer:true,options:[]},
            {orderNo:4,prompt:'Таймер',correct:false,timedOut:true,answer:null,correctAnswer:false,options:[]}
        ]});
        h.setState({...h.getState(),status:'finished',currentRound:null});h.refresh();await flush();
        const review=d.querySelector('#qg-review');
        assert.equal(d.querySelector('#qg-result-count').textContent,'1 / 4');
        assert.equal(review.querySelectorAll('article').length,4);
        assert.equal(review.querySelector('h4').textContent,prompt);
        assert.equal(review.querySelector('img,script,a'),null);
        assert.equal(review.querySelector('.qg-review-explanation p').textContent,explanation);
        assert.equal(review.querySelector('.qg-review-answers').textContent,'yourAnswer: Не верю');
        assert.equal(review.querySelectorAll('article')[1].querySelectorAll('.qg-review-answers strong').length,2);
        assert.equal(d.querySelector('[data-review-count="wrong"]').textContent,'1');
        assert.equal(d.querySelector('[data-review-count="unanswered"]').textContent,'2');
        d.querySelector('[data-review-filter="unanswered"]').click();
        assert.equal(review.querySelectorAll('article').length,2);
        assert.match(review.textContent,/skippedShort/);assert.match(review.textContent,/timedOutShort/);
        d.querySelector('[data-review-filter="wrong"]').click();
        assert.equal(review.querySelectorAll('article').length,1);
        assert.equal(review.querySelector('h4').textContent,'Ошибка');
        assert.equal(d.querySelector('[data-review-filter="wrong"]').getAttribute('aria-pressed'),'true');
        d.querySelector('[data-review-filter="all"]').click();assert.equal(review.querySelectorAll('article').length,4);
    } finally {h.close();}
});
test('карточки разбора отображают снимки выбора, перевода, соответствий и пропусков',async()=>{
    const examples=[
        {options:[{id:'A',text:'Первый'},{id:'B',text:'Второй'}],answer:{selected:['A','B']},expected:'Первый, Второй'},
        {options:[{id:'B',text:'Перевод'}],answer:{selected:['B']},expected:'Перевод'},
        {options:{left:[{id:'1',text:'Один'}],right:[{id:'a',text:'Первый'}]},answer:{pairs:{'1':'a'}},expected:'Один → Первый'},
        {options:{},answer:{answers:{'[[1]]':'30'}},expected:'[[1]]: 30'}
    ];
    for(const example of examples){
        const h=await harness(),d=h.w.document;
        try{
            d.querySelector('[data-quiz-game-room]').dataset.initialStatus='finished';
            h.setResults({me:{place:1,score:1,maxScore:1,correctAnswers:1,totalRounds:1,percent:100},leaderboard:[],review:[{orderNo:1,prompt:'Вопрос',correct:true,answer:example.answer,correctAnswer:example.answer,options:example.options}]});
            h.setState({...h.getState(),status:'finished',currentRound:null});h.refresh();await flush();
            assert.equal(d.querySelector('.qg-review-answers strong').textContent,example.expected);
            assert.equal(d.querySelector('.qg-review-explanation'),null);
            d.querySelector('[data-review-filter="wrong"]').click();assert.equal(d.querySelectorAll('#qg-review article').length,0);
            assert.equal(d.querySelector('.qg-review-empty').textContent,'reviewEmpty');
        }finally{h.close();}
    }
});
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
        const d=h.w.document;d.querySelector('input[value="B"]').checked=true;await h.refresh();await flush();assert.equal(d.querySelector('input[value="B"]').checked,true);
        h.setState({...h.getState(),currentRound:{...h.getState().currentRound,id:2,orderNo:2,prompt:'Следующий'}});await h.refresh();await flush();
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
    const h=await harness();try{h.setState({...h.getState(),currentRound:{...h.getState().currentRound,endsAt:new Date(Date.now()-1000).toISOString()}});await h.refresh();await flush();
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
        h.setState(lobby);await h.refresh();await flush();
        assert.equal(d.querySelector('#qg-start').hidden,false);assert.equal(d.querySelector('#qg-ready').hidden,true);
        d.querySelector('#qg-invite').click();assert.equal(d.querySelector('#qg-invitation').hidden,false);
        d.querySelector('#qg-invite').click();assert.equal(d.querySelector('#qg-invitation').hidden,true);
        d.querySelector('#qg-count').value='7';d.querySelector('#qg-count').dispatchEvent(new h.w.Event('input',{bubbles:true}));
        await h.refresh();await flush();assert.equal(d.querySelector('#qg-count').value,'7');
        h.setState({...lobby,settingsRevision:1,questionCount:3});await h.refresh();await flush();
        assert.equal(d.querySelector('#qg-count').value,'3');
    }finally{h.close();}
});

for(const terminal of ['cancelled','expired'])test(`${terminal}: фокус на завершении и отсутствие игровых действий`,async()=>{
    const h=await harness();try{
        h.setState({...h.getState(),status:terminal,currentRound:null});await h.refresh();await flush();
        const d=h.w.document;assert.equal(d.activeElement.id,'qg-terminal-title');
        assert.equal(d.querySelector('#qg-terminal').hidden,false);assert.equal(d.querySelector('#qg-active').hidden,true);
        assert.equal(d.querySelector('#qg-lobby').hidden,true);assert.equal(h.requests.length,0);
    }finally{h.close();}
});
