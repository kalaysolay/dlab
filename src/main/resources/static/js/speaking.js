/* Реальный текстовый срез: данные принадлежат серверу; ответы и наблюдения не хранятся в localStorage. */
(() => {
    'use strict';
    const root = document.querySelector('#speaking-app');
    if (!root) return;
    const screen = document.querySelector('#sp-screen');
    const error = document.querySelector('#sp-error');
    const status = document.querySelector('#sp-status');
    const kk = root.dataset.language === 'kk';
    const words = {
        choose:['О чём поговорим?','Не туралы сөйлесеміз?'], intro:['Выбери ситуацию. Собеседник начнёт первым.','Жағдайды таңда. Әңгімені көмекші бастайды.'],
        select:['Выбрать','Таңдау'], back:['← Все ситуации','← Барлық жағдай'], start:['Начать разговор','Әңгімені бастау'],
        level:['Сложность','Күрделілік'], language:['Язык объяснений','Түсіндірме тілі'], roles:['Историю и детали можно придумать. Настоящие имена, адреса и контакты не нужны.','Оқиғаны ойдан шығаруға болады. Нақты аты-жөн, мекенжай және байланыс деректері қажет емес.'],
        active:['Продолжить','Жалғастыру'], finish:['Закончить','Аяқтау'], confirmFinish:['Закончить текущий разговор?','Әңгімені аяқтаймыз ба?'],
        turn:['Ответ','Жауап'], of:['из','/'], learner:['Ты · текст','Сен · мәтін'], agent:['DamuLab · тестовый собеседник','DamuLab · сынақ көмекшісі'],
        send:['Ответить','Жауап беру'], answer:['Твоя очередь. Ответь на вопрос выше','Сенің кезегің. Жоғарыдағы сұраққа жауап бер'],
        hint:['Подсказка','Көмек'], sample:['Подставить тестовый пример с ошибкой','Қатесі бар сынақ мысалын қою'],
        correction:['Полезное исправление','Пайдалы түзету'], dispute:['Не согласен с исправлением','Түзетумен келіспеймін'], disputed:['Оспорено — не учитывается','Даулы — есептелмейді'],
        fresh:['↓ К свежему','↓ Соңғы хабарламаға'], history:['Мои разговоры','Менің әңгімелерім'], empty:['Пока нет разговоров','Әзірге әңгіме жоқ'],
        delete:['Удалить','Жою'], deleteConfirm:['Удалить разговор и связанные замечания?','Әңгімені және ескертулерін жою керек пе?'],
        ended:['Разговор завершён','Әңгіме аяқталды'], finished:['Закончен раньше','Ертерек аяқталды'], expired:['Сессия истекла','Сессия мерзімі бітті'],
        busy:['Сохраняем…','Сақталуда…'], loading:['Загружаем…','Жүктелуде…'], trouble:['Трудности в разговорной практике','Сөйлесу жаттығуындағы қиындықтар'],
        none:['Пока нет повторяющихся замечаний. Это не оценка уровня английского.','Қайталанатын ескертулер әзірге жоқ. Бұл ағылшын тілі деңгейінің бағасы емес.'],
        recurring:['Повторяется','Қайталанады'], observed:['Есть наблюдение','Ескерту бар'], count:['независимых ответов','тәуелсіз жауап'],
        help:['Можно начать так:','Былай бастауға болады:'], demo:['В тестовом режиме проверяется только заранее подготовленный пример Past Simple. Остальные ответы сохраняются без языковой диагностики.','Сынақ режимінде тек дайын Past Simple мысалы тексеріледі. Басқа жауаптар тілдік бағалаусыз сақталады.'],
        cleared:['Текст удалён по сроку хранения','Мәтін сақтау мерзімі бойынша жойылды'],
        assisted:['С подсказкой — не учитывается как самостоятельная ошибка','Көмекпен — дербес қате ретінде есептелмейді']
    };
    words.accepted=['Принято ответов','Қабылданған жауаптар'];
    words.inProgress=['В процессе','Жалғасуда'];
    words.count=['Наблюдений по разным ответам','Әртүрлі жауаптардағы ескертулер'];
    const t = key => words[key]?.[kk ? 1 : 0] || key;
    const skillName = code => code==='grammar.past_simple'
        ? (kk?'Past Simple · өткен шақ':'Past Simple · прошедшее время')
        : (kk?'Тілдік дағды':'Языковой навык');
    const sessionStatus = value => t(value==='ACTIVE'?'inProgress':value==='COMPLETED'?'ended':value==='EXPIRED'?'expired':'finished');
    const title = s => kk ? s.titleKk : s.titleRu;
    const api = '/api/student/speaking';
    let scenarios = [], current = null, busy = false, draft = '', pending = null, observer;
    const csrf = document.querySelector('meta[name="_csrf"]')?.content;
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]')?.content || 'X-CSRF-TOKEN';

    // Любой серверный или ученический текст вставляется через textContent, без HTML/Markdown.
    function el(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text !== undefined) node.textContent = text;
        return node;
    }
    function button(label, action, primary = false) {
        const node = el('button', `button${primary ? ' primary' : ''}`, label);
        node.type = 'button'; node.disabled = busy; node.addEventListener('click', action); return node;
    }
    function link(label, hash, className = 'button') {
        const node = el('a', className, label); node.href = hash; return node;
    }
    function setBusy(value) {
        busy = value; root.setAttribute('aria-busy', String(value));
        screen.querySelectorAll('button, textarea, select').forEach(node => node.disabled = value);
        status.textContent = value ? t('busy') : '';
    }
    function showError(code) {
        const messages = {
            speaking_active_session_exists: kk ? 'Белсенді әңгіме бар. Оны тарихтан жалғастыр.' : 'Уже есть активная беседа. Продолжи её из истории.',
            speaking_version_conflict: kk ? 'Әңгіме басқа қойындыда өзгерді. Жаңартылған сұрақты тексер.' : 'Беседа изменилась в другой вкладке. Проверь обновлённый вопрос.',
            speaking_daily_limit: kk ? 'Бүгінгі 3 әңгіме шегіне жеттің.' : 'Достигнут лимит: 3 новых разговора в день.',
            speaking_session_closed: kk ? 'Әңгіме жабық немесе мерзімі біткен.' : 'Беседа завершена или истекло время ожидания.',
            speaking_not_found: kk ? 'Әңгіме табылмады.' : 'Беседа не найдена или недоступна.',
            speaking_pilot_required: kk ? 'Бұл бөлімге қолжетімділік әлі ашылмады.' : 'Доступ к этому разделу ещё не открыт.'
        };
        error.textContent = messages[code] || (kk ? 'Сұрау орындалмады. Мәтініңді тексеріп, қайталап көр.' : 'Не удалось выполнить запрос. Твой текст остался в поле — можно повторить попытку.');
        error.hidden = false;
    }
    async function request(path, method = 'GET', body, key) {
        const response = await fetch(api + path, {method, credentials:'same-origin', cache:'no-store',
            headers:{'Content-Type':'application/json', ...(csrf ? {[csrfHeader]:csrf} : {}), ...(key ? {'Idempotency-Key':key} : {})},
            ...(body === undefined ? {} : {body:JSON.stringify(body)})});
        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            const failure = new Error(data.code || 'network'); failure.status = response.status; throw failure;
        }
        return response.status === 204 ? null : response.json();
    }
    async function run(action) {
        if (busy) return;
        const initialHash=location.hash;
        error.hidden = true; setBusy(true);
        try { await action(); }
        catch (failure) { showError(failure.message); }
        finally {
            // Навигация во время запроса не должна оставлять данные старой беседы под новым URL.
            try { if(location.hash!==initialHash)await route(); }
            catch(failure){showError(failure.message);}
            setBusy(false);
        }
    }
    function clearScreen() { observer?.disconnect(); screen.replaceChildren(); }

    function renderCatalog() {
        current = null; clearScreen();
        screen.append(el('h2','',t('choose')), el('p','',t('intro')));
        const grid = el('section','sp-grid');
        for (const scenario of scenarios) {
            const card=el('article','sp-card');
            card.append(el('h2','',title(scenario)), el('small','',scenario.englishTitle),
                el('p','',kk ? scenario.descriptionKk : scenario.descriptionRu),
                link(t('select'), '#setup/' + scenario.code));
            grid.append(card);
        }
        screen.append(grid);
    }
    function renderSetup(code) {
        const scenario=scenarios.find(s => s.code === code);
        if (!scenario) { showError('speaking_not_found'); return; }
        clearScreen(); screen.append(link(t('back'),'#catalog','sp-back'));
        const card=el('section','sp-setup'); card.append(el('h2','',title(scenario)),el('p','',scenario.englishTitle),
            el('p','',kk ? scenario.descriptionKk : scenario.descriptionRu),el('p','',t('roles')));
        const level=el('select');
        for (const [value,label] of [['A2',kk?'Жеңіл':'Проще'],['B1',kk?'Орташа':'Обычно'],['B2',kk?'Күрделі':'Сложнее']]) {
            const option=el('option','',`${label} · ${value}`); option.value=value; option.selected=value==='B1'; level.append(option);
        }
        const language=el('select');
        for (const [value,label] of [['ru','Русский'],['kk','Қазақша']]) {
            const option=el('option','',label); option.value=value; option.selected=value===(kk?'kk':'ru'); language.append(option);
        }
        for (const [label,input] of [[t('level'),level],[t('language'),language]]) {
            const field=el('label','sp-field',label); field.append(input); card.append(field);
        }
        card.append(button(t('start'),()=>run(async()=>{
            const body={scenarioCode:code,level:level.value,explanationLanguage:language.value};
            // Потерянный ответ HTTP можно повторить тем же ключом, не создавая вторую сессию.
            const signature=JSON.stringify(body);
            if (!pending || pending.signature!==signature) pending={signature,key:crypto.randomUUID()};
            const session=await request('/sessions','POST',body,pending.key);
            pending=null; draft=''; current=session; location.hash='#session/'+session.id;
        }),true)); screen.append(card);
    }

    function addAgent(chat, reaction, question) {
        if (!reaction && !question) return;
        const node=el('article','sp-message'); node.dataset.speaker='assistant';
        node.append(el('div','sp-message-label',t('agent')));
        if (reaction) node.append(el('p','',reaction));
        if (question) node.append(el('p','sp-question',question));
        chat.append(node);
    }
    function renderSession(session) {
        const old=screen.querySelector('.sp-chat');
        const previous=old?{top:old.scrollTop,bottom:old.scrollHeight-old.clientHeight-old.scrollTop<32,count:Number(old.dataset.count)}:null;
        current=session; clearScreen(); screen.append(link(t('back'),'#catalog','sp-back'));
        const head=el('div','sp-row sp-session-head'); const copy=el('div');
        copy.append(el('h2','',title(session.scenario)),el('small','',`${session.level} · ${t('accepted')}: ${session.acceptedTurns} / 8`));head.append(copy);
        if (session.status==='ACTIVE') head.append(button(t('finish'),()=>{
            if (window.confirm(t('confirmFinish'))) run(async()=>{current=await request(`/sessions/${session.id}/finish`,'POST',{expectedVersion:session.version});draft='';renderSession(current);});
        }));screen.append(head);
        const progress=el('div','sp-progress');const bar=el('span');bar.style.width=`${session.acceptedTurns/8*100}%`;progress.append(bar);screen.append(progress);
        const frame=el('section','sp-chat-frame');const chat=el('div','sp-chat');
        chat.tabIndex=0;chat.role='region';chat.setAttribute('aria-label',kk?'Әңгіме тарихы':'История разговора');chat.dataset.count=session.acceptedTurns;
        addAgent(chat,session.openingText,session.firstQuestion);
        for (const turn of session.turns) {
            const node=el('article','sp-message sp-learner');node.dataset.speaker='learner';
            node.append(el('div','sp-message-label',t('learner')),el('p','',turn.text || t('cleared')));
            for (const note of session.observations.filter(o=>o.turnId===turn.id)) {
                const details=el('details','sp-feedback');details.append(el('summary','',`${t('correction')} · ${skillName(note.skillCode)}`));
                const change=el('p');change.append(el('del','',note.original),document.createTextNode(' → '),el('strong','',note.corrected));
                details.append(change,el('p','',note.explanation));
                if(note.assisted)details.append(el('small','',t('assisted')));
                if(note.evidence==='disputed') details.append(el('small','',t('disputed')));
                else details.append(button(t('dispute'),()=>run(async()=>{
                    const updated=await request(`/sessions/${session.id}/observations/${note.id}/dispute`,'POST',{expectedVersion:session.version});renderSession(updated);
                })));
                node.append(details);
            }
            chat.append(node);addAgent(chat,turn.reactionText,turn.questionText);
        }
        const jump=button(t('fresh'),()=>chat.scrollTo({top:chat.scrollHeight,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'}));
        jump.className='sp-jump';jump.setAttribute('aria-label',t('fresh'));
        const update=()=>{jump.hidden=chat.scrollHeight-chat.clientHeight-chat.scrollTop<32;};
        frame.append(chat,jump);screen.append(frame);
        chat.scrollTop=previous?(session.acceptedTurns>previous.count&&previous.bottom?chat.scrollHeight:previous.top):(session.acceptedTurns?chat.scrollHeight:0);
        chat.addEventListener('scroll',update,{passive:true});observer=new ResizeObserver(update);observer.observe(chat);for(const node of chat.children)observer.observe(node);update();
        const composer=el('section','sp-compose');
        if(session.status==='ACTIVE') {
            const label=el('label','sp-field',t('answer'));const input=el('textarea');input.id='sp-answer';input.maxLength=2000;input.value=draft;
            input.addEventListener('input',()=>{draft=input.value;});label.append(input);composer.append(label);
            composer.append(button(t('send'),()=>run(async()=>{
                const text=input.value.trim();if(!text)return;
                const path=`/sessions/${session.id}/turns`;
                // При сетевой ошибке повторяем именно прежнее тело и ключ; 409 требует перечитать беседу.
                if(!pending || pending.path!==path || pending.text!==text)pending={path,text,key:crypto.randomUUID(),body:{text,expectedVersion:session.version}};
                try {
                    const updated=await request(path,'POST',pending.body,pending.key);pending=null;draft='';renderSession(updated);
                } catch(failure) {
                    if(failure.status>=400&&failure.status<500){pending=null;current=await request('/sessions/'+session.id);renderSession(current);}
                    throw failure;
                }
            }),true));
            const actions=el('div','sp-actions');actions.append(button(t('hint'),()=>run(async()=>{
                const previousHelp=composer.querySelector('.sp-help');if(previousHelp){previousHelp.remove();return;}
                const updated=await request(`/sessions/${session.id}/help`,'POST',{expectedVersion:session.version});renderSession(updated);
                const help=el('div','sp-help',t('help'));session.scenario.hints.forEach(h=>help.append(el('p','',h)));screen.querySelector('.sp-compose').append(help);
            })));
            if(session.scenario.code==='weekend_story')actions.append(button(t('sample'),()=>run(async()=>{
                const updated=await request(`/sessions/${session.id}/help`,'POST',{expectedVersion:session.version});draft='Yesterday I go to the cinema.';renderSession(updated);
            })));
            composer.append(actions,el('p','sp-mode',t('demo')));
        } else {
            composer.append(el('h2','',t(session.status==='COMPLETED'?'ended':session.status==='EXPIRED'?'expired':'finished')),
                el('p','',`${t('turn')}: ${session.acceptedTurns} / 8`),link(t('choose'),'#catalog'));
        }
        screen.append(composer);
    }
    async function renderHistory() {
        const [items,difficulties]=await Promise.all([request('/sessions'),request('/difficulties')]);
        clearScreen();screen.append(link(t('back'),'#catalog','sp-back'),el('h2','',t('history')));
        if(!items.length)screen.append(el('p','',t('empty')));
        for(const item of items){
            const card=el('article','sp-history');const text=el('div');
            text.append(el('h2','',item.title),el('small','',`${new Date(item.createdAt).toLocaleDateString(kk?'kk-KZ':'ru-RU')} · ${item.acceptedTurns}/8 · ${sessionStatus(item.status)}`));
            card.append(text,link(t('active'),'#session/'+item.id),button(t('delete'),()=>{
                if(window.confirm(t('deleteConfirm')))run(async()=>{await request('/sessions/'+item.id,'DELETE');await renderHistory();});
            }));screen.append(card);
        }
        const section=el('section','sp-difficulties');section.append(el('h2','',t('trouble')));
        if(!difficulties.length)section.append(el('p','',t('none')));
        for(const item of difficulties)section.append(el('p','',`${skillName(item.skillCode)} · ${t(item.status)}. ${t('count')}: ${item.observations}`));
        screen.append(section);
    }
    async function route() {
        const [page,id]=(location.hash.slice(1)||'catalog').split('/');
        if(page==='session'&&id){renderSession(await request('/sessions/'+encodeURIComponent(id)));}
        else if(page==='setup')renderSetup(id);
        else if(page==='history')await renderHistory();
        else renderCatalog();
    }
    window.addEventListener('hashchange',()=>{run(route);});
    run(async()=>{status.textContent=t('loading');scenarios=await request('/scenarios');await route();});
})();
