/* Данные беседы принадлежат серверу. Звук живёт только в памяти вкладки до отправки/отмены. */
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
    Object.assign(words,{
        record:['🎙 Ответить голосом','🎙 Дауыспен жауап беру'],stop:['■ Закончить запись','■ Жазуды тоқтату'],
        recording:['Идёт запись','Жазылып жатыр'],recognizing:['Распознаём запись…','Жазбаны тануда…'],
        heard:['Я услышал так. Проверь и при необходимости поправь текст','Мен осылай естідім. Мәтінді тексеріп, қажет болса түзет'],
        confirmVoice:['Верно, ответить','Дұрыс, жауап беру'],again:['Перезаписать','Қайта жазу'],
        typed:['Ответить текстом','Мәтінмен жауап беру'],upload:['Выбрать аудиозапись','Аудиожазбаны таңдау'],
        cancel:['Отменить запись','Жазуды болдырмау'],retry:['Повторить отправку записи','Жазбаны қайта жіберу'],
        permission:['Разреши доступ к микрофону в браузере. Можно также выбрать запись или ответить текстом.','Браузерде микрофонға рұқсат бер. Жазбаны таңдауға немесе мәтінмен жауап беруге де болады.'],
        interrupted:['Запись прервана. Начни заново, когда будешь готов.','Жазба үзілді. Дайын болғанда қайта баста.'],
        voiceInfo:['До 60 секунд. После остановки появится текст — отправишь его после проверки. Запись обрабатывается на сервере DamuLab и затем удаляется.','60 секундқа дейін. Тоқтаған соң мәтін шығады — тексеріп, өзің жібересің. Жазба DamuLab серверінде өңделіп, содан кейін жойылады.'],
        voiceOff:['Голосовой ввод сейчас недоступен. Можно ответить текстом.','Дауыстық енгізу қазір қолжетімсіз. Мәтінмен жауап беруге болады.'],
        voiceSource:['Ты · голос, текст подтверждён','Сен · дауыс, мәтін расталды'],editedSource:['Ты · текст после правки расшифровки','Сен · танылған мәтінді түзеттің'],
        editedNote:['Текст расшифровки изменён — замечание не учитывается как самостоятельная речевая ошибка','Танылған мәтін өзгертілді — ескерту дербес сөйлеу қатесі ретінде есептелмейді'],
        requesting:['Ожидаем разрешения микрофона…','Микрофон рұқсатын күтудеміз…']
    });
    Object.assign(words,{
        liveAgent:['DamuLab · ИИ-собеседник','DamuLab · ЖИ көмекшісі'],
        liveMode:['Практика с ИИ: он может ошибаться. Проверь распознанный текст и предложенные исправления.','ЖИ-мен жаттығу: ол қателесуі мүмкін. Танылған мәтін мен ұсынылған түзетулерді тексер.'],
        stubMode:['Тестовый собеседник по сценарию. Проверяется только подготовленный пример; это не оценка уровня.','Сценарий бойынша сынақ көмекшісі. Тек дайын мысал тексеріледі; бұл тіл деңгейінің бағасы емес.'],
        liveInfo:['Исправления предлагает ИИ. Если замечание неверное, нажми «Не согласен с исправлением».','Түзетулерді ЖИ ұсынады. Ескерту қате болса, «Түзетумен келіспеймін» түймесін бас.'],
        listen:['Послушать','Тыңдау'],slow:['Помедленнее','Баяуырақ'],speechVoice:['Голос создан ИИ','Дауыс ЖИ арқылы жасалған'],
        thinking:['Собеседник готовит ответ и проверяет его…','Көмекші жауапты дайындап, тексеруде…'],
        notStored:['Сообщение не сохранено. Продолжим безопасную практику.','Хабарлама сақталмады. Қауіпсіз жаттығуды жалғастырайық.'],
        support:['Сделаем паузу','Үзіліс жасайық'],supportNote:['Сейчас важнее получить помощь взрослого. Исправления английского здесь отключены.','Қазір ересек адамның көмегі маңыздырақ. Ағылшын тілін түзету бұл жерде тоқтатылды.'],
        change:['Выбрать другую ситуацию','Басқа жағдайды таңдау'],style:['Можно сказать иначе','Басқаша айтуға болады']
    });
    if(root.dataset.stt==='deepgram')words.voiceInfo=['До 60 секунд. Запись распознаёт Deepgram без использования для обучения. Проверь текст перед отправкой собеседнику.','60 секундқа дейін. Жазбаны Deepgram оқыту үшін пайдаланбай таниды. Көмекшіге жібермес бұрын мәтінді тексер.'];
    const t = key => words[key]?.[kk ? 1 : 0] || key;
    const skillNames={
        'grammar.past_simple':['Past Simple · прошедшее время','Past Simple · өткен шақ'],
        'grammar.present_simple':['Present Simple','Present Simple'],'grammar.present_continuous':['Present Continuous','Present Continuous'],
        'grammar.present_perfect':['Present Perfect','Present Perfect'],'grammar.future':['Будущее время','Келер шақ'],
        'grammar.articles':['Артикли','Артикльдер'],'grammar.prepositions':['Предлоги','Предлогтар'],'grammar.word_order':['Порядок слов','Сөз тәртібі'],
        'grammar.agreement':['Согласование','Сөздердің қиысуы'],'grammar.plural':['Множественное число','Көпше түр'],
        'grammar.comparatives':['Сравнения','Салыстыру'],'grammar.questions':['Вопросы','Сұрақтар'],'grammar.negation':['Отрицание','Болымсыздық'],
        'vocabulary.word_choice':['Выбор слова','Сөз таңдау']
    };
    const skillName=code=>skillNames[code]?.[kk?1:0]||(kk?'Тілдік дағды':'Языковой навык');
    const sessionStatus = value => t(value==='PAUSED_SUPPORT'?'support':value==='ACTIVE'?'inProgress':value==='COMPLETED'?'ended':value==='EXPIRED'?'expired':'finished');
    const title = s => kk ? s.titleKk : s.titleRu;
    const api = '/api/student/speaking';
    let scenarios = [], current = null, busy = false, draft = '', pending = null, observer;
    let voice=null, recording=null, voiceEpoch=0, typing=false;
    let operation=null,operationEpoch=0,speechEpoch=0,playing=null;
    const speechCache=new Map();
    const mode=document.querySelector('#sp-mode');
    mode.textContent=t(root.dataset.live==='true'?'liveMode':'stubMode');
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
        node.type = 'button'; node.disabled = busy || !!recording; node.addEventListener('click', action); return node;
    }
    function link(label, hash, className = 'button') {
        const node = el('a', className, label); node.href = hash; return node;
    }
    function setBusy(value) {
        busy = value; root.setAttribute('aria-busy', String(value));
        screen.querySelectorAll('button, textarea, select, input').forEach(node => node.disabled = value);
        status.textContent = value ? t('busy') : '';
    }
    function showError(code) {
        const messages = {
            speaking_dialogue_unavailable:kk?'Көмекші қазір қолжетімсіз. Мәтін сақталды, қайта жіберуге болады.':'Собеседник сейчас недоступен. Текст остался в поле — можно повторить.',
            speaking_provider_failed:kk?'ЖИ қызметі жауап бермеді. Қайта жіберуге болады.':'Сервис ИИ не ответил. Можно повторить отправку.',
            speaking_provider_timeout:kk?'ЖИ жауабы тым ұзаққа созылды. Қайталап көр.':'Сервис ИИ отвечает слишком долго. Повтори попытку.',
            speaking_provider_busy:kk?'ЖИ қызметі бос емес. Сәл күте тұр.':'Сервис ИИ занят. Повтори чуть позже.',
            speaking_generation_processing:kk?'Алдыңғы жауап әлі дайындалуда. Сәл күтіп, қайтала.':'Предыдущий ответ ещё готовится. Подожди немного и повтори.',
            speaking_generation_expired:kk?'Жауапты дайындау мерзімі бітті. Қайталап көр.':'Время подготовки ответа истекло. Повтори попытку.',
            speaking_llm_daily_limit:kk?'Бүгінгі ЖИ сұрауларының шегіне жеттің.':'На сегодня достигнут лимит обращений к собеседнику.',
            speaking_speech_daily_limit:kk?'Бүгінгі дыбыстау шегіне жеттің. Сұрақты оқуға болады.':'На сегодня достигнут лимит озвучки. Можно прочитать вопрос.',
            speaking_speech_unavailable:kk?'Дыбыстау қолжетімсіз. Сұрақты оқуға болады.':'Озвучка недоступна. Можно прочитать вопрос.',
            speech_play:kk?'Ойнату басталмады. «Тыңдау» түймесін қайта бас.':'Воспроизведение не началось. Нажми «Послушать» ещё раз.',
            speaking_voice_unavailable:t('voiceOff'), speaking_audio_size:kk?'Жазба 10 МБ-тан аспауы тиіс.':'Запись должна быть не больше 10 МБ.',
            speaking_audio_duration:kk?'0,4–60 секундтық жазбаны таңда.':'Нужна запись длительностью от 0,4 до 60 секунд.',
            speaking_audio_invalid:kk?'Бұл аудионы оқу мүмкін емес. Қайта жазып көр.':'Не удалось прочитать аудио. Попробуй записать заново.',
            speaking_audio_empty:kk?'Сөзді анық ести алмадым. Қайта жазып көр.':'Не удалось уверенно распознать речь. Попробуй перезаписать.',
            speaking_voice_failed:kk?'Дыбысты тану орындалмады. Қайталап көр.':'Не удалось распознать запись. Попробуй ещё раз.',
            speaking_voice_timeout:kk?'Дыбысты тану тым ұзаққа созылды. Қайталап көр.':'Распознавание заняло слишком много времени. Попробуй ещё раз.',
            speaking_voice_busy:kk?'Дыбысты тану бос емес. Сәлден кейін қайталап көр.':'Распознавание сейчас занято. Повтори чуть позже.',
            speaking_voice_processing:kk?'Алдыңғы жазба әлі өңделуде.':'Предыдущая запись ещё обрабатывается. Подожди немного.',
            speaking_voice_expired:kk?'Жазба мерзімі бітті. Қайта жаз.':'Черновик записи истёк или уже использован. Перезапиши ответ.',
            speaking_voice_rate_limit:kk?'Бір минутта 6 жазбадан артық емес. Сәл күте тұр.':'Не больше 6 записей в минуту. Подожди немного.',
            speaking_audio_daily_limit:kk?'Бүгінгі аудио шегіне жеттің. Мәтінмен жалғастыруға болады.':'Дневной лимит аудио исчерпан. Можно продолжить текстом.',
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
            signal:operation?.signal,
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
        const token=++operationEpoch;operation=new AbortController();
        const initialHash=location.hash;
        error.hidden = true; setBusy(true);
        try { await action(); }
        catch (failure) { if(token===operationEpoch)showError(failure.message); }
        finally {
            if(token!==operationEpoch)return;
            // Навигация во время запроса не должна оставлять данные старой беседы под новым URL.
            try { if(location.hash!==initialHash)await route(); }
            catch(failure){if(token===operationEpoch)showError(failure.message);}
            if(token===operationEpoch){setBusy(false);operation=null;}
        }
    }
    function clearScreen() {
        stopSpeech();
        observer?.disconnect();
        screen.querySelectorAll('audio').forEach(audio=>{audio.pause();audio.removeAttribute('src');audio.load();});
        screen.replaceChildren();
    }
    function stopSpeech(){if(playing){playing.pause();playing.removeAttribute('src');playing.load();playing=null;}}
    function clearSpeech(){speechEpoch++;stopSpeech();for(const url of speechCache.values())URL.revokeObjectURL(url);speechCache.clear();}
    function playQuestion(session,turnId,slow){
        if(recording)return;
        run(async()=>{
            const epoch=speechEpoch,key=session.id+'/'+(turnId||'first');
            let url=speechCache.get(key);
            if(!url){
                const response=await fetch(`${api}/sessions/${session.id}/speech`,{method:'POST',credentials:'same-origin',cache:'no-store',signal:operation?.signal,
                    headers:{'Content-Type':'application/json',...(csrf?{[csrfHeader]:csrf}:{})},body:JSON.stringify({turnId})});
                if(!response.ok){const data=await response.json().catch(()=>({}));throw new Error(data.code||'speaking_speech_unavailable');}
                const blob=await response.blob();if(epoch!==speechEpoch)return;
                url=URL.createObjectURL(blob);speechCache.set(key,url);
            }
            if(epoch!==speechEpoch)return;
            screen.querySelectorAll('audio').forEach(audio=>audio.pause());stopSpeech();playing=new Audio(url);playing.playbackRate=slow?0.75:1;
            try{await playing.play();}catch(failure){throw new Error('speech_play');}
        });
    }

    // При уходе со страницы/в фон останавливаем микрофон, но не запускаем распознавание.
    function clearVoice() {
        voiceEpoch++;
        if(recording) {
            recording.cancelled=true;clearInterval(recording.timer);
            if(recording.recorder?.state==='recording')recording.recorder.stop();
            recording.stream?.getTracks().forEach(track=>track.stop());recording=null;
        }
        screen.querySelectorAll('audio').forEach(audio=>{audio.pause();audio.removeAttribute('src');audio.load();});
        if(voice?.url)URL.revokeObjectURL(voice.url);
        voice=null;
    }
    async function transcribe(session,blob) {
        const epoch=voiceEpoch,hash=location.hash;
        if(!voice || voice.blob!==blob)voice={blob,key:crypto.randomUUID(),url:URL.createObjectURL(blob),version:session.version};
        const attempt=voice;
        await run(async()=>{
            attempt.failed=false;
            renderSession(session);status.textContent=t('recognizing');
            try {
                const response=await fetch(`${api}/sessions/${session.id}/transcriptions?expectedVersion=${attempt.version}`,{
                    method:'POST',credentials:'same-origin',cache:'no-store',signal:operation?.signal,headers:{'Content-Type':blob.type||'application/octet-stream',
                        ...(csrf?{[csrfHeader]:csrf}:{}),'Idempotency-Key':attempt.key},body:blob});
                const result=await response.json().catch(()=>({}));
                if(!response.ok){const failure=new Error(result.code||'network');failure.status=response.status;throw failure;}
                if(epoch!==voiceEpoch||hash!==location.hash)return;
                attempt.transcript=result;draft=result.text;pending=null;typing=true;renderSession(session);
                screen.querySelector('#sp-answer')?.focus({preventScroll:true});
            } catch(failure) {
                if(epoch!==voiceEpoch||hash!==location.hash)return;
                if(['speaking_version_conflict','speaking_session_closed'].includes(failure.message)) {
                    clearVoice();draft='';pending=null;
                    const updated=await request('/sessions/'+session.id);
                    if(hash===location.hash)renderSession(updated);
                    throw failure;
                }
                // Потеря сети не доказывает отказ: сохраняем ключ. Явный HTTP-отказ завершает попытку;
                // исключение — ещё идущая обработка, которую нужно перечитать тем же ключом.
                if(failure.status&&failure.message!=='speaking_voice_processing')attempt.key=crypto.randomUUID();
                attempt.failed=true;renderSession(session);throw failure;
            }
        });
    }
    async function beginRecording(session) {
        if(busy||recording)return;
        clearVoice();draft='';pending=null;error.hidden=true;
        stopSpeech();
        const epoch=voiceEpoch;
        const state={cancelled:false,stream:null,recorder:null,timer:null};recording=state;renderSession(session);
        try {
            const stream=await navigator.mediaDevices.getUserMedia({audio:{echoCancellation:true,noiseSuppression:true},video:false});
            if(state.cancelled||epoch!==voiceEpoch){stream.getTracks().forEach(track=>track.stop());return;}
            state.stream=stream;
            const mime=['audio/webm;codecs=opus','audio/mp4','audio/webm','audio/ogg;codecs=opus'].find(value=>MediaRecorder.isTypeSupported(value));
            const recorder=new MediaRecorder(stream,mime?{mimeType:mime}:{});state.recorder=recorder;
            const chunks=[];let bytes=0;
            recorder.addEventListener('dataavailable',event=>{
                if(state.cancelled||epoch!==voiceEpoch)return;
                bytes+=event.data.size;if(bytes>10*1024*1024){cancelRecording(session,'speaking_audio_size');return;}
                if(event.data.size)chunks.push(event.data);
            });
            recorder.addEventListener('stop',()=>{
                clearInterval(state.timer);stream.getTracks().forEach(track=>track.stop());
                if(recording===state)recording=null;
                if(state.cancelled||epoch!==voiceEpoch)return;
                const blob=new Blob(chunks,{type:recorder.mimeType});
                transcribe(session,blob);
            });
            recorder.addEventListener('error',()=>{if(!state.cancelled&&epoch===voiceEpoch)cancelRecording(session,'interrupted');});
            stream.getAudioTracks().forEach(track=>track.addEventListener('ended',()=>{
                if(recording===state)cancelRecording(session,'interrupted');
            }));
            state.started=Date.now();recorder.start(500);renderSession(session);
            state.timer=setInterval(()=>{
                const seconds=Math.min(60,Math.floor((Date.now()-state.started)/1000));
                const timer=screen.querySelector('.sp-recording');if(timer)timer.textContent=`● ${t('recording')} · ${seconds} / 60`;
                if(Date.now()-state.started>=59900 && recorder.state==='recording')recorder.stop();
            },200);
        } catch(failure) {
            if(state.cancelled||epoch!==voiceEpoch)return;
            clearVoice();renderSession(session);showError('permission');error.textContent=t('permission');
        }
    }
    function cancelRecording(session,code) {
        clearVoice();renderSession(session);if(code){error.textContent=words[code]?t(code):'';if(!words[code])showError(code);error.hidden=false;}
    }
    function voiceControls(composer,session) {
        if(!session.voiceAvailable){composer.append(el('p','sp-voice-info',t('voiceOff')));return;}
        const panel=el('div','sp-voice');
        if(recording) {
            panel.append(el('p','sp-recording',recording.recorder?`${t('recording')} · 0 / 60`:t('requesting')));
            if(recording.recorder){const stop=button(t('stop'),()=>{if(recording?.recorder?.state==='recording')recording.recorder.stop();},true);stop.disabled=false;panel.append(stop);}
            const cancel=button(t('cancel'),()=>cancelRecording(session));cancel.disabled=false;panel.append(cancel);
        } else if(voice?.transcript) {
            const audio=el('audio');audio.controls=true;audio.src=voice.url;audio.preload='metadata';panel.append(audio);
            panel.append(button(t('again'),()=>run(async()=>{
                await request(`/sessions/${session.id}/transcriptions/${voice.transcript.id}`,'DELETE');
                clearVoice();draft='';pending=null;typing=false;renderSession(session);
            }).then(()=>{
                if(!voice&&location.hash==='#session/'+session.id&&navigator.mediaDevices?.getUserMedia&&window.MediaRecorder)beginRecording(session);
            })));
        } else if(busy&&voice&&!voice.failed) {
            panel.append(el('p','',t('recognizing')));
        } else {
            if(navigator.mediaDevices?.getUserMedia&&window.MediaRecorder)panel.append(button(t('record'),()=>beginRecording(session),true));
            if(voice?.failed)panel.append(button(t('retry'),()=>transcribe(session,voice.blob)));
            const label=el('label','button sp-file',t('upload'));const file=el('input');file.type='file';file.accept='audio/*';file.disabled=busy;
            file.addEventListener('change',()=>{const selected=file.files[0];if(!selected)return;
                if(selected.size>10*1024*1024){showError('speaking_audio_size');file.value='';return;}
                clearVoice();draft='';pending=null;transcribe(session,selected);
            });label.append(file);panel.append(label);
            panel.append(button(t('typed'),()=>{typing=true;renderSession(session);screen.querySelector('#sp-answer')?.focus();}));
        }
        panel.append(el('small','sp-voice-info',t('voiceInfo')));composer.append(panel);
    }

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

    function addAgent(chat, reaction, question,session,turnId=null) {
        if (!reaction && !question) return;
        const node=el('article','sp-message'); node.dataset.speaker='assistant';
        node.append(el('div','sp-message-label',t(session.providerMode==='LIVE'?'liveAgent':'agent')));
        if (reaction) node.append(el('p','',reaction));
        if (question) node.append(el('p','sp-question',question));
        if(question&&session.speechAvailable&&session.status!=='PAUSED_SUPPORT'){
            const controls=el('div','sp-actions sp-speech');controls.append(button('🔊 '+t('listen'),()=>playQuestion(session,turnId,false)),
                button('◷ '+t('slow'),()=>playQuestion(session,turnId,true)),el('small','sp-voice-info',t('speechVoice')));node.append(controls);
        }
        chat.append(node);
    }
    function renderSession(session) {
        const old=screen.querySelector('.sp-chat');
        const previous=old?{top:old.scrollTop,bottom:old.scrollHeight-old.clientHeight-old.scrollTop<32,count:Number(old.dataset.count)}:null;
        current=session; clearScreen(); screen.append(link(t('back'),'#catalog','sp-back'));
        mode.textContent=t(session.providerMode==='LIVE'?'liveMode':'stubMode');
        const head=el('div','sp-row sp-session-head'); const copy=el('div');
        copy.append(el('h2','',title(session.scenario)),el('small','',`${session.level} · ${t('accepted')}: ${session.acceptedTurns} / 8`));head.append(copy);
        if (session.status==='ACTIVE'||session.status==='PAUSED_SUPPORT') head.append(button(t('finish'),()=>{
                if (window.confirm(t('confirmFinish'))) run(async()=>{current=await request(`/sessions/${session.id}/finish`,'POST',{expectedVersion:session.version});draft='';clearVoice();renderSession(current);});
        }));screen.append(head);
        const progress=el('div','sp-progress');const bar=el('span');bar.style.width=`${session.acceptedTurns/8*100}%`;progress.append(bar);screen.append(progress);
        const frame=el('section','sp-chat-frame');const chat=el('div','sp-chat');
        chat.tabIndex=0;chat.role='region';chat.setAttribute('aria-label',kk?'Әңгіме тарихы':'История разговора');chat.dataset.count=session.turns.length;
        addAgent(chat,session.openingText,session.firstQuestion,session);
        for (const turn of session.turns) {
            const node=el('article','sp-message sp-learner');node.dataset.speaker='learner';
            node.append(el('div','sp-message-label',t(turn.source==='voice'?'voiceSource':turn.source==='edited'?'editedSource':'learner')),
                el('p','',turn.disposition&&turn.disposition!=='NORMAL'?t('notStored'):(turn.text || t('cleared'))));
            for (const note of session.observations.filter(o=>o.turnId===turn.id)) {
                const details=el('details','sp-feedback');details.append(el('summary','',`${t(note.kind==='style'?'style':'correction')} · ${skillName(note.skillCode)}`));
                const change=el('p');change.append(el('del','',note.original),document.createTextNode(' → '),el('strong','',note.corrected));
                details.append(change,el('p','',note.explanation));
                if(note.assisted)details.append(el('small','',t('assisted')));
                if(note.source==='edited')details.append(el('small','',t('editedNote')));
                if(note.evidence==='disputed') details.append(el('small','',t('disputed')));
                else details.append(button(t('dispute'),()=>run(async()=>{
                    const updated=await request(`/sessions/${session.id}/observations/${note.id}/dispute`,'POST',{expectedVersion:session.version});renderSession(updated);
                })));
                node.append(details);
            }
            chat.append(node);addAgent(chat,turn.reactionText,turn.questionText,session,turn.id);
        }
        const jump=button(t('fresh'),()=>chat.scrollTo({top:chat.scrollHeight,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'}));
        jump.className='sp-jump';jump.setAttribute('aria-label',t('fresh'));
        const update=()=>{jump.hidden=chat.scrollHeight-chat.clientHeight-chat.scrollTop<32;};
        frame.append(chat,jump);screen.append(frame);
        chat.scrollTop=previous?(session.turns.length>previous.count&&previous.bottom?chat.scrollHeight:previous.top):(session.turns.length?chat.scrollHeight:0);
        chat.addEventListener('scroll',update,{passive:true});observer=new ResizeObserver(update);observer.observe(chat);for(const node of chat.children)observer.observe(node);update();
        const composer=el('section','sp-compose');
        if(session.status==='ACTIVE') {
            voiceControls(composer,session);
            const showText=!recording&&(!session.voiceAvailable||typing||voice?.transcript);
            const label=el('label','sp-field',t(voice?.transcript?'heard':'answer'));const input=el('textarea');input.id='sp-answer';input.maxLength=2000;input.value=draft;
            input.addEventListener('input',()=>{draft=input.value;});label.append(input);composer.append(label);
            label.hidden=!showText;
            const send=button(t(voice?.transcript?'confirmVoice':'send'),()=>run(async()=>{
                const text=input.value.trim();if(!text)return;
                if(session.providerMode==='LIVE')status.textContent=t('thinking');
                const path=`/sessions/${session.id}/turns`;
                const voiceAttemptId=voice?.transcript?.id||null;
                // При сетевой ошибке повторяем именно прежнее тело и ключ; 409 требует перечитать беседу.
                if(!pending || pending.path!==path || pending.text!==text || pending.body.voiceAttemptId!==voiceAttemptId)
                    pending={path,text,key:crypto.randomUUID(),body:{text,expectedVersion:session.version,voiceAttemptId}};
                try {
                    const updated=await request(path,'POST',pending.body,pending.key);pending=null;draft='';clearVoice();typing=false;renderSession(updated);
                } catch(failure) {
                    if(failure.status>=400&&failure.status<500){pending=null;current=await request('/sessions/'+session.id);renderSession(current);}
                    throw failure;
                }
            }),true);send.hidden=!showText;composer.append(send);
            const actions=el('div','sp-actions');actions.append(button(t('hint'),()=>run(async()=>{
                const previousHelp=composer.querySelector('.sp-help');if(previousHelp){previousHelp.remove();return;}
                const updated=await request(`/sessions/${session.id}/help`,'POST',{expectedVersion:session.version});clearVoice();renderSession(updated);
                const help=el('div','sp-help',t('help'));session.scenario.hints.forEach(h=>help.append(el('p','',h)));screen.querySelector('.sp-compose').append(help);
            })));
            if(session.providerMode==='STUB'&&session.scenario.code==='weekend_story')actions.append(button(t('sample'),()=>run(async()=>{
                const updated=await request(`/sessions/${session.id}/help`,'POST',{expectedVersion:session.version});clearVoice();typing=true;draft='Yesterday I go to the cinema.';renderSession(updated);
            })));
            if(session.changeSuggested)actions.append(button(t('change'),()=>run(async()=>{
                await request(`/sessions/${session.id}/finish`,'POST',{expectedVersion:session.version});clearVoice();draft='';pending=null;location.hash='#catalog';
            })));
            composer.append(actions,el('p','sp-mode',t(session.providerMode==='LIVE'?'liveInfo':'demo')));
        } else {
            composer.append(el('h2','',t(session.status==='PAUSED_SUPPORT'?'support':session.status==='COMPLETED'?'ended':session.status==='EXPIRED'?'expired':'finished')),
                ...(session.status==='PAUSED_SUPPORT'?[el('p','',t('supportNote'))]:[]),
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
    window.addEventListener('hashchange',()=>{
        // Уход не ждёт долгой генерации. Сервер сам проверит lease/версию, поздний ответ не меняет новую страницу.
        operation?.abort();operationEpoch++;busy=false;clearVoice();clearSpeech();draft='';pending=null;typing=false;run(route);
    });
    window.addEventListener('pagehide',()=>{operation?.abort();clearVoice();clearSpeech();});
    document.addEventListener('visibilitychange',()=>{if(document.hidden&&recording)cancelRecording(current,'interrupted');});
    run(async()=>{status.textContent=t('loading');scenarios=await request('/scenarios');await route();});
})();
