/* Автономный кликабельный макет. Настоящий микрофон, API, хранение речи и ИИ не используются. */
'use strict';
const screen = document.querySelector('#screen');
const scenarios = [
  {id:'movie',icon:'film',color:'purple',title:'Мой любимый фильм',en:'My Favourite Movie',description:'Расскажи о героях и сюжете. Посоветуй фильм и объясни, почему он тебе нравится.',role:'собеседник о кино',you:'зритель и автор рекомендации',goals:['Назови фильм или придумай свой','Расскажи, что понравилось и почему','Посоветуй фильм другому зрителю'],questions:['What is a film you really like?','What do you like about the main character?','What is your favourite part of the film?','Why did you enjoy that part?','How is it different from other films you know?','Who would enjoy watching this film?','Would you watch it again? Why?','How would you recommend this film in one sentence?'],sample:'My favourite film is Inside Out. I like it because the characters are interesting.',hint:['My favourite film is…','I like it because…','I would recommend it to…']},
  {id:'holiday',icon:'calendar-days',color:'amber',title:'Планы на праздник',en:'New Year and Holiday Plans',description:'Придумай идеальный праздник: занятия, игры и то, чего ждёшь больше всего.',role:'собеседник о планах',you:'автор праздничного плана',goals:['Выбери Новый год или другой праздник','Расскажи, что собираешься делать','Объясни, чего ждёшь больше всего'],questions:['What would you like to do for New Year or another holiday?','What are you going to do first?','Which activity are you looking forward to?','Why would you choose that activity?','What game could you play?','What could you do if the weather changes?','What would make this holiday special?','How would you describe your plan in one sentence?'],sample:'I am going to celebrate New Year. I would like to play games and watch a film.',hint:['I am going to…','First, we could…','I would like to…']},
  {id:'weekend',icon:'sun',color:'mint',title:'Как прошли выходные',en:'My Weekend',description:'Поделись маленькой историей: что было сначала, что потом и что запомнилось.',role:'внимательный собеседник',you:'рассказчик своей истории',goals:['Расскажи об одном событии','Объясни, что было сначала и потом','Поделись своим впечатлением'],questions:['What did you do last weekend? You can tell a real or imaginary story.','What did you watch?','What did you enjoy most about it?','What happened after that?','How did you feel about your day?','Was it different from your usual weekend?','What would you like to do again?','What was the best part of your weekend?'],sample:'Yesterday I go to the cinema. I watched an interesting film.',hint:['Last weekend, I…','After that, I…','The best part was…']},
  {id:'hotel',icon:'hotel',color:'purple',title:'Бронируем отель',en:'Booking a Hotel Room',description:'Выбери номер в вымышленном отеле. Спроси о цене, завтраке и удобствах.',role:'сотрудник Maple Hotel',you:'участник вымышленной поездки',goals:['Попроси подходящий номер','Задай вопрос о цене или удобствах','Подтверди выбор или вежливо откажись'],questions:['Welcome to our imaginary hotel. What kind of room would you like?','For how many imaginary nights would you like to stay?','A double room is £60 per night. What would you like to ask about it?','Breakfast is £5 per person. Would you like to include it?','What else would you like to know about the room?','Would you prefer a different type of room?','Could you confirm your room and number of nights?','Thank you! What did you choose in our role-play?'],sample:'I would like a double room for two nights. Is breakfast included?',hint:['I would like a… room','How much is…?','Is breakfast included?']},
  {id:'shop',icon:'shopping-bag',color:'rose',title:'Покупки в магазине',en:'Shopping at a Store',description:'Найди нужную вещь, уточни цвет или размер и выбери подходящий вариант.',role:'продавец в учебном магазине',you:'покупатель в ролевой игре',goals:['Скажи, что ищешь','Спроси о цене, цвете или размере','Выбери вещь или вежливо откажись'],questions:['Hello! Would you like a T-shirt, a notebook or a backpack?','Which colour would you like?','What would you like to know about the item?','The T-shirt is £15. Would you like to try a different option?','Would you like to compare it with another item?','Is there anything else you would like to ask?','Which option would you choose?','How would you politely finish this conversation?'],sample:'I am looking for a blue T-shirt. Do you have it in size M?',hint:['I am looking for…','Do you have it in…?','How much does it cost?']}
];
let chosen = scenarios[2];
let level = 'B1', language = 'ru', mode = 'idle', messages = [], answers = 0, draft = '', hint = '', special = '', saved = null;
let toastTimer, recordingTimer, seconds = 0;
const escapeHtml = value => String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const originalIcons = new Set(['sparkles','home','clipboard-check','bar-chart-3','trophy','user','map','shield-check','book-open-check','star','languages','trash-2']);
function icon(name){
  // Используются реальные иконки приложения и локальные SVG из библиотеки Lucide.
  name = ({'gamepad-2':'trophy','award':'star'})[name] || name;
  if(name==='mic')return "<svg\n  class=\"lucide-icon\" aria-hidden=\"true\"\n  xmlns=\"http://www.w3.org/2000/svg\"\n  width=\"24\"\n  height=\"24\"\n  viewBox=\"0 0 24 24\"\n  fill=\"none\"\n  stroke=\"currentColor\"\n  stroke-width=\"2\"\n  stroke-linecap=\"round\"\n  stroke-linejoin=\"round\"\n>\n  <path d=\"M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z\" />\n  <path d=\"M19 10v2a7 7 0 0 1-14 0v-2\" />\n  <line x1=\"12\" x2=\"12\" y1=\"19\" y2=\"22\" />\n</svg>";
  return originalIcons.has(name) ? `<svg class="lucide-icon" aria-hidden="true"><use href="../../src/main/resources/static/icons/lucide-sprite.svg#${name}"></use></svg>` : `<img class="lucide-icon" src="icons/${name}.svg" alt="">`;
}
function hydrate(){document.querySelectorAll('[data-icon]').forEach(el=>{el.innerHTML=icon(el.dataset.icon);});}
const btn = (label,action,style='secondary') => `<button type="button" class="button ${style}" data-action="${action}">${label}</button>`;
const tile = s => `<span class="icon-tile ${s.color}">${icon(s.icon)}</span>`;
const crumbs = (text,href='#catalog') => `<a class="crumb" href="${href}">${icon('arrow-left')}${text}</a>`;
function notify(message){const t=document.querySelector('#toast');t.textContent=message;t.hidden=false;clearTimeout(toastTimer);toastTimer=setTimeout(()=>t.hidden=true,5000);}
function home(){return `<div class="student-home">
  <section class="student-home-hero"><p class="kicker">Мой кабинет · 6 класс</p><h1>Привет, Алия! Какую вершину покорим сегодня?</h1><p class="muted">Маленькие шаги каждый день — большие открытия впереди.</p></section>
  <section class="student-home-grid student-home-grid-top"><article class="panel"><div class="card-title-row">${icon('map')}<h2>Миссия дня</h2></div><div class="mission-box"><strong>Один шаг к уверенным знаниям</strong><small>Пройди короткий тест и узнай, что уже хорошо получается.</small></div><button class="button primary" data-notice="В макете работает разговорник. Нажми пункт «Разговорный английский» между уроками и переводчиком.">Начать тест</button></article><article class="panel student-word-card"><div class="card-title-row">${icon('shield-check')}<h2>Слово дня</h2></div><p class="kicker">Запоминайка</p><h1 style="margin:8px 0">Жігер</h1><p class="muted">Настойчивость и воля двигаться к цели.</p></article></section>
  <div class="home-tools"><a class="student-lessons-link" href="#" data-notice="Уроки — существующий раздел DamuLab."><span class="student-lessons-link-icon">${icon('book-open-check')}</span><span class="student-lessons-link-copy"><strong>Уроки</strong><small>Читай и закрепляй знания</small></span><span class="chev">›</span></a><a class="student-lessons-link student-speaking-link" href="#catalog"><span class="student-lessons-link-icon">${icon('mic')}</span><span class="student-lessons-link-copy"><strong>Разговорный английский</strong><small>Практикуй английский с ИИ по 5–10 минут</small></span><span class="chev">›</span></a><a class="student-lessons-link student-translator-link" href="#" data-notice="Переводчик — существующий раздел DamuLab."><span class="student-lessons-link-icon">${icon('languages')}</span><span class="student-lessons-link-copy"><strong>Переводчик</strong><small>Казахский и русский с разбором</small></span><span class="chev">›</span></a></div>
  <section class="student-home-grid"><article class="panel"><div class="card-title-row">${icon('book-open-check')}<h2>Последнее занятие</h2></div><strong>Математика · Обыкновенные дроби</strong><p class="muted">8 из 10 верно. Хороший темп! Продолжай укреплять знания.</p><button class="button secondary" data-notice="Демонстрационный результат ученика.">Посмотреть результат</button></article><article class="panel"><div class="card-title-row">${icon('star')}<h2>Серия активности</h2></div><h1>3 дня</h1><p class="muted">подряд занятий · лучший результат: 7 дней</p></article></section>
  <section class="panel"><h2>Статистика прогресса</h2><div class="home-mini"><div><small>Пройдено тестов</small><strong>12</strong></div><div><small>Средний результат</small><strong>82%</strong></div><div><small>Достижения</small><strong>4</strong></div></div></section>
  </div>`;}
function catalog(){return `${crumbs('Главная ученика','#home')}<div class="catalog-intro"><div><p class="kicker">Английский · разговорная практика</p><h1>О чём поговорим?</h1><p>Выбери ситуацию. Слушай вопросы и отвечай голосом.</p></div><a class="button secondary" href="#history">${icon('clock')}Мои разговоры</a></div><div class="facts"><span>${icon('clock')}5–10 минут</span><span>${icon('mic')}Голосом или текстом</span><span>${icon('shield-check')}Темы для школьников</span></div>
  <section class="scenario-grid" aria-label="Ситуации для разговора">${scenarios.map(s=>`<a class="scenario-card" href="#setup/${s.id}">${tile(s)}<div><h2>${s.title}</h2><div class="english-name">${s.en}</div></div><p>${s.description}</p><div class="card-end"><small>8 ответов · 5–10 мин</small>${icon('arrow-right')}</div></a>`).join('')}</section>
  <div class="note-panel section-gap">${icon('shield-check')}<p><strong>Можно ошибаться и придумывать.</strong><br>Это практика, а не контрольная. ИИ поможет с фразами и мягко подскажет, что поправить. Настоящие имена, адреса и контакты не нужны.</p></div>`;}
function setup(){return `${crumbs('Все ситуации')}<div class="setup-title">${tile(chosen)}<div><p class="kicker">Разговорный английский</p><h1>${chosen.title}</h1><p>${chosen.en}</p></div></div><div class="split"><div class="stack"><article class="panel"><h2>Твоя задача</h2><p class="muted">${chosen.description} Историю можно придумать.</p><div class="role-label">Ты — <strong>${chosen.you}</strong>.<br>ИИ — ${chosen.role}.</div><ol class="goal-list">${chosen.goals.map((g,i)=>`<li><span class="step-no">${i+1}</span><div>${g}</div></li>`).join('')}</ol><div class="tag-row"><span class="pill neutral">5–10 минут</span><span class="pill neutral">8 ответов</span><span class="pill neutral">Без оценок</span></div></article><div class="note-panel">${icon('headphones')}<p>Найди спокойное место. Можно слушать через наушники. Каждый вопрос будет виден текстом, а подсказка всегда рядом.</p></div></div><aside class="panel"><h2>Как будем говорить?</h2><p class="muted" style="font-size:13px">Выбери комфортную сложность. Это не проверка уровня.</p><span class="field-label">Сложность беседы</span><div class="segmented">${[['A2','Проще'],['B1','Обычно'],['B2','Сложнее']].map(([l,t])=>`<button data-level="${l}" aria-pressed="${level===l}" class="${level===l?'selected':''}"><strong>${t}</strong><small>${l}</small></button>`).join('')}</div><label class="field-label" for="explanation">Язык объяснений</label><select id="explanation" class="select-input"><option value="ru" ${language==='ru'?'selected':''}>Русский</option><option value="kk" ${language==='kk'?'selected':''}>Қазақша</option></select>${btn(`${icon('mic')} Начать разговор`,'start','primary wide')}<p class="privacy-note">Вопросы — на английском. Объяснения — на выбранном языке. В макете запись и ответы имитируются: разрешение на микрофон не запрашивается.</p></aside></div>`;}
function question(){
  if(answers===0 && level!=='A2') {
    const openings={movie:['Which film would you recommend, and what do you like about it?','Which film has stayed with you, and what makes it worth recommending?'],holiday:['Choose a holiday. What are you planning to do, and why?','What would your ideal holiday celebration look like, and how would you organise it?'],weekend:['Tell me about your weekend. What happened first?','What made your last real or imaginary weekend memorable?'],hotel:['Welcome to Maple Hotel. What room would you like for your imaginary trip?','Welcome to Maple Hotel. How can I help you choose a room for this role-play?'],shop:['Welcome to our imaginary shop. What are you looking for today?','Welcome! What would you like to find in our shop, and what matters most to you?']};
    return openings[chosen.id][level==='B2'?1:0];
  }
  return chosen.questions[Math.min(answers,7)];
}
// Первую реплику даёт собеседник: приветствие и вопрос появляются до любого ответа ребёнка.
function openingText(){return {
  movie:"Hi! Let's talk about films. I'll ask you a few questions, and you can ask me for help at any time.",
  holiday:"Hi! Let's imagine a holiday together. I'll help you make a plan, one question at a time.",
  weekend:"Hi! Let's talk about your weekend. You can tell me a real story or make one up. I'll start with a question.",
  hotel:"Hello! I'm the receptionist in our imaginary hotel. Let's practise choosing a room together.",
  shop:"Hello! I'm the shop assistant in our imaginary shop. Let's find something you like together."
}[chosen.id];}
function firstAnswerGuide(){return {
  movie:'Назови фильм, который тебе нравится. Можно выбрать мультфильм или придумать свой.',
  holiday:'Выбери праздник и назови одно занятие, которое тебе хочется попробовать.',
  weekend:'Расскажи об одном занятии на выходных. Историю можно придумать.',
  hotel:'Скажи, какой номер тебе нужен. Все детали поездки можно придумать.',
  shop:'Выбери футболку, блокнот или рюкзак и скажи, что ищешь.'
}[chosen.id];}
function renderComposer(){
  if(answers>=8)return '<p>Разговор завершён. Все вопросы и ответы доступны выше.</p><a class="button secondary" href="#summary">Посмотреть итог</a>';
  if(mode==='recording')return `<div class="composer-heading"><span class="rec-dot" style="color:#c94e53"></span>Демонстрация записи <span id="record-time">00:${String(seconds).padStart(2,'0')}</span></div>${btn(`${icon('square')} Остановить запись`,'stop','primary wide record-button recording')}<p class="composer-hint">Микрофон не включён · ответ будет подставлен из примера</p><div class="helper-actions"><button data-action="cancel-record">Отменить</button></div>`;
  if(mode==='confirm')return `<div class="composer-heading">Я услышал… <span class="pill neutral">Проверь текст</span></div><button type="button" class="button quiet" data-action="play-preview">${icon('volume-2')}Прослушать запись</button><textarea id="answer-input" aria-label="Распознанный ответ">${escapeHtml(draft)}</textarea><div class="actions">${btn('Перезаписать','record')}${btn('Верно, ответить','confirm','primary')}</div><p class="composer-hint">Текст можно поправить. Ошибку распознавания не считаем твоей.</p>`;
  if(mode==='typed')return `<label for="answer-input" class="field-label">Твой ответ на английском</label><textarea id="answer-input" maxlength="2000" placeholder="Напиши свой ответ…">${escapeHtml(draft)}</textarea><div class="actions">${btn('Отмена','cancel-record')}${btn('Ответить','confirm','primary')}</div><div class="helper-actions"><button data-action="sample">Подставить пример ответа</button></div>`;
  if(mode==='busy')return `<div class="composer-heading" role="status">Готовим ответ…</div><p class="muted">Демонстрация распознавания и проверки.</p>`;
  return `<div class="composer-heading">${answers?'Твоя очередь':'Теперь твоя очередь'}</div>${!answers?`<p class="first-answer-guide">${firstAnswerGuide()}</p><p class="answer-starter">Можно начать: <strong>${chosen.hint[0]}</strong></p>`:''}${btn(`${icon('mic')} Ответить на вопрос` ,'record','primary wide record-button')}<p class="composer-hint">Сначала послушай или прочитай вопрос выше · запись в макете имитируется</p><div class="helper-actions"><button data-action="typed">Ответить текстом</button><button data-action="hint">Подсказка</button><button data-action="rephrase">Не понял вопрос</button></div>`;
}
function specialPanel(){
  if(special==='support')return `<article class="panel support"><span class="pill neutral">Разговор на паузе</span><h2 style="margin-top:16px">Сейчас важнее твоя безопасность</h2><p>Спасибо, что рассказал(а). Обратись к взрослому, которому доверяешь. Если опасность прямо сейчас — попроси немедленной помощи и свяжись с местной экстренной службой.</p><p class="muted">Практика приостановлена. Здесь не будет исправлений английского.</p><a class="button secondary" href="#catalog">Вернуться к ситуациям</a></article>`;
  const data={denied:['Нет доступа к микрофону','Разреши доступ к микрофону в настройках сайта или продолжи текстом. Макет не запрашивает системные разрешения.'],offline:['Нет соединения','Мы пока не знаем, принят ли последний ответ. Когда сеть вернётся, проверим состояние разговора.'],interrupted:['Запись прервана','Приложение свернули или запись прервал звонок. Чтобы продолжить, запиши ответ ещё раз.'],restricted:['Продолжим безопасную тему','Let’s keep this activity suitable for children. Вернёмся к нашей ситуации.'],offtopic:['Вернёмся к нашей ситуации','Let’s come back to our activity. Ты можешь продолжить или выбрать другую ситуацию.'],tts:['Не удалось включить звук','Вопрос остаётся на экране. Можно продолжить текстом или повторить воспроизведение.'],uncertain:['Не удалось уверенно разобрать ответ','Попробуй записать ещё раз или введи текст. Это не ошибка английского.']}[special];
  return `<article class="panel"><h2>${data[0]}</h2><p class="muted">${data[1]}</p><div class="actions">${btn(special==='offline'?'Сеть восстановлена':special==='interrupted'||special==='uncertain'?'Записать заново':'Продолжить','recover','primary')}<a class="button secondary" href="#catalog">Выбрать ситуацию</a></div></article>`;
}
// Сохраняем именно показанный вопрос рядом с ответом, чтобы история не зависела от текущего шага.
function assistantMessage(index,text,intro=''){
  return `<article class="message question" data-speaker="assistant" data-message-index="${index}"><div class="message-label">${icon('sparkles')} DAMULAB · ИИ-СОБЕСЕДНИК</div>${intro?`<span class="pill blue">Собеседник начинает</span><p class="opening-text">${escapeHtml(intro)}</p>`:'<p class="muted" style="font-size:13px;margin-bottom:8px">Thanks for sharing. Let’s continue our conversation.</p>'}<p class="question-text">${escapeHtml(text)}</p><div class="listen-actions"><button data-action="listen" data-question-index="${index}">${icon('volume-2')}Послушать</button><button data-action="listen-slow" data-question-index="${index}">${icon('turtle')}Помедленнее</button></div></article>`;
}
function learnerMessage(message,index){
  return `<article class="message learner" data-speaker="learner"><div class="message-label">ТЫ · ${message.source}</div><p>${escapeHtml(message.text)}</p>${message.correction?`<details class="correction" data-correction-index="${index}"><summary>Одно полезное исправление</summary><p><span class="wrong">Yesterday I go</span> → <span class="right">Yesterday I went</span></p><p>${language==='kk'?'Өткен шақ туралы айтқанда go орнына went қолданылады.':'Речь о прошлом: после yesterday здесь нужна форма went.'}</p><button class="button quiet" data-notice="Демо: здесь можно будет оспорить замечание и исключить его из учебных наблюдений.">Не согласен с исправлением</button></details>`:''}</article>`;
}
function renderTranscript(){
  const turns=messages.map((message,index)=>assistantMessage(index,message.questionText,message.opening)+learnerMessage(message,index)).join('');
  const current=answers<8?assistantMessage(answers,question(),answers===0?openingText():''):'';
  return `<section class="chat-frame" aria-label="Переписка"><div id="chat-history" class="chat-history" role="region" aria-label="История разговора" tabindex="0" data-answer-count="${answers}"><div class="chat-thread">${turns}${current}</div></div><button type="button" class="latest-message-button" data-action="latest-message" aria-controls="chat-history" aria-label="К свежему сообщению" title="К свежему сообщению" hidden>${icon('arrow-right')}<span>К свежему</span></button></section>`;
}
let chatResizeObserver;
function updateChatScroll(){
  const history=document.querySelector('#chat-history');
  const button=document.querySelector('.latest-message-button');
  if(!history||!button)return;
  button.hidden=history.scrollHeight-history.clientHeight-history.scrollTop<32;
}
function restoreChatScroll(previous){
  const history=document.querySelector('#chat-history');
  if(!history)return;
  // Если ученик читает старые реплики, новые не отнимают у него позицию прокрутки.
  // У свежего сообщения следуем за диалогом автоматически; при первом входе показываем приветствие сверху.
  history.scrollTop=previous?(answers>previous.answers&&previous.atBottom?history.scrollHeight:previous.top):(answers?history.scrollHeight:0);
  for(const index of previous?.openCorrections||[]) {
    const details=document.querySelector('[data-correction-index="'+index+'"]');
    if(details)details.open=true;
  }
  history.addEventListener('scroll',updateChatScroll,{passive:true});
  chatResizeObserver=new ResizeObserver(updateChatScroll);
  chatResizeObserver.observe(history);
  chatResizeObserver.observe(history.querySelector('.chat-thread'));
  updateChatScroll();
}
function conversation(){return `${crumbs('Ситуации')}<div class="conversation-head"><div><h1>${chosen.title}</h1><p class="subtitle">ИИ-собеседник · ${level} · Ответ ${Math.min(answers+1,8)} из 8</p></div>${btn('Закончить','finish')}</div><div class="progress-line"><span style="width:${answers/8*100}%"></span></div><div class="conversation-layout"><div>${special?specialPanel():`${renderTranscript()}${hint?`<div class="help-box"><strong>${hint==='hint'?'ПОПРОБУЙ НАЧАТЬ ТАК':'ВОПРОС ПРОЩЕ'}</strong>${hint==='hint'?chosen.hint.map(t=>`<p>${t}</p>`).join(''):`<p>${escapeHtml(chosen.questions[0])}</p>`}<button class="button quiet" data-action="close-help">Скрыть подсказку</button></div>`:''}<section class="composer" aria-label="Ответ ученика">${renderComposer()}</section>`}
  <details class="demo-controls"><summary>Состояния макета</summary><div class="demo-buttons">${[['denied','Нет разрешения на микрофон'],['offtopic','Уход от темы'],['restricted','Ограничение темы'],['support','Поддерживающая пауза'],['offline','Нет сети'],['interrupted','Звонок / сворачивание'],['tts','Ошибка звука'],['uncertain','Неясная речь']].map(([s,t])=>`<button data-special="${s}">${t}</button>`).join('')}</div></details></div><aside class="stack"><div class="side-card"><span class="kicker">Твоя ситуация</span><h2>${chosen.en}</h2><p>${chosen.description}</p><ul>${chosen.goals.map(g=>`<li>${g}</li>`).join('')}</ul></div><div class="note-panel"><p><strong>Можно ошибаться.</strong><br>Подсказки не уменьшают результат. Это разговор, а не тест.</p></div><p class="muted" style="font-size:11px">Макет: реплики заранее подготовлены, микрофон и звук имитируются.</p></aside></div>`;}
function summary(){const scenario=saved?.scenario||chosen;const items=saved?.messages||messages;const displayLevel=saved?.level||level;const count=saved?.count??answers;return `${crumbs('Все ситуации')}<div class="summary-hero"><span class="icon-tile mint">${icon('circle-check')}</span><p class="kicker">${count===8?'Разговор завершён':'Разговор закончен раньше'}</p><h1>${count?'Ещё один шаг к уверенному английскому':'Попробуем в другой раз'}</h1><p class="muted">${scenario.title} · ${displayLevel}<br>${count?'Спасибо за практику! Вот что получилось в этой демонстрации.':'Начать новую ситуацию можно в любой момент.'}</p></div><div class="summary-grid"><article><strong>${count}</strong><span>ответов из 8</span></article><article><strong>${items.filter(m=>m.source==='голос · демо').length}</strong><span>голосовых ответов · демо</span></article><article><strong>${items.filter(m=>m.correction).length}</strong><span>полезных исправлений</span></article></div><div class="split section-gap"><section class="panel"><h2>Что можно потренировать</h2>${items.some(m=>m.correction)?'<p><strong>Past Simple · прошедшее время</strong></p><p class="muted">В примере встретилось “Yesterday I go”. Для прошлого события подходит “Yesterday I went”.</p>':'<p class="muted">В выбранных примерах нет отмеченных ошибок. Это не оценка владения языком.</p>'}<div class="note-panel"><p>Произношение в первой версии не оцениваем. В макете показаны только демонстрационные наблюдения.</p></div></section><section class="panel"><p class="kicker">Следующий маленький шаг</p><h2>Попробуй другую ситуацию</h2><p class="muted">Меняй темы и учись объяснять свои мысли по-разному.</p><div class="actions"><a class="button primary" href="#catalog">Выбрать ситуацию</a><a class="button secondary" href="#history">Мои разговоры</a></div></section></div>`;}
function history(){return `${crumbs('Разговорный английский')}<div class="row"><h1>Мои разговоры</h1><span class="pill neutral">Демо</span></div><p class="muted">Твоя практика и полезные замечания. История макета хранится только до перезагрузки страницы.</p><section class="panel section-gap">${saved?`<article class="history-item">${tile(saved.scenario)}<div class="copy"><h2>${saved.scenario.title}</h2><p>Сегодня · ${saved.level} · ${saved.count} ответов · ${saved.count===8?'Завершён':'Закончен раньше'}</p></div><a class="button secondary" href="#summary">Итог</a><button type="button" class="button quiet" data-action="delete-history" aria-label="Удалить разговор">${icon('trash-2')}</button></article>`:`<div style="text-align:center;padding:28px"><span class="muted">Пока нет разговоров</span><h2 style="margin-top:12px">Начни с интересной ситуации</h2><p class="muted">После практики здесь появятся итоги и замечания.</p><a class="button primary" href="#catalog">Выбрать ситуацию</a></div>`}</section>`;}
function render({keepScroll=false}={}){
  const oldHistory=document.querySelector('#chat-history');
  const previous=oldHistory?{top:oldHistory.scrollTop,atBottom:oldHistory.scrollHeight-oldHistory.clientHeight-oldHistory.scrollTop<32,answers:Number(oldHistory.dataset.answerCount),openCorrections:[...oldHistory.querySelectorAll('details[open]')].map(el=>el.dataset.correctionIndex)}:null;
  chatResizeObserver?.disconnect();
  const [route,id]=(location.hash.slice(1)||'home').split('/');
  if(route==='setup')chosen=scenarios.find(s=>s.id===id)||scenarios[2];
  const page=({home,catalog,setup,conversation,summary,history})[route]||home;
  screen.innerHTML=page();hydrate();restoreChatScroll(previous);document.querySelector('[data-nav="home"]').classList.toggle('active',route==='home');
  document.title=`${route==='home'?'Кабинет ученика':route==='catalog'?'Разговорный английский':chosen.title} — DamuLab`;
  if(!keepScroll){window.scrollTo(0,0);screen.focus({preventScroll:true});}
}
function stopClock(){clearInterval(recordingTimer);recordingTimer=null;}
// Остановка сразу запускает распознавание; отдельного подтверждения отправки на ASR нет.
// В макете результат синхронно подставляется из примера, без микрофона и внешнего API.
function finishRecording(){
  stopClock();
  draft=answers===0?chosen.sample:chosen.id==='weekend'?'I watched an animated film. It was funny.':'I think it is interesting because I can try something new.';
  mode='confirm';
  render({keepScroll:true});
}
function startRecord(){stopClock();mode='recording';seconds=0;draft='';hint='';render({keepScroll:true});recordingTimer=setInterval(()=>{seconds++;const el=document.querySelector('#record-time');if(el)el.textContent=`00:${String(seconds).padStart(2,'0')}`;if(seconds>=60)finishRecording();},1000);}
function confirmAnswer(){const input=document.querySelector('#answer-input');const text=input?.value.trim();if(!text)return notify('Сначала запиши или введи ответ.');if(text.length>2000)return notify('Ответ должен быть короче 2000 символов.');const source=mode==='typed'?'текст':draft===text?'голос · демо':'исправленный текст';messages.push({text,source,questionText:question(),opening:answers===0?openingText():'',correction:/yesterday i go\b/i.test(text)});answers++;mode='idle';draft='';hint='';if(answers>=8){complete();}else render({keepScroll:true});}
function complete(){stopClock();saved={count:answers,scenario:chosen,level,messages:messages.map(m=>({...m}))};mode='idle';special='';location.hash='summary';}
function openModal(html){document.querySelector('#modal-body').innerHTML=html;document.querySelector('#modal').showModal();hydrate();}
document.addEventListener('click',event=>{
  const el=event.target.closest('[data-action],[data-special],[data-notice],[data-level]');if(!el)return;
  if(el.dataset.notice){event.preventDefault();notify(el.dataset.notice);return;}
  if(el.dataset.level){level=el.dataset.level;language=document.querySelector('#explanation').value;render({keepScroll:true});return;}
  if(el.dataset.special){stopClock();special=el.dataset.special;mode='idle';render({keepScroll:true});return;}
  switch(el.dataset.action){
    case 'start': language=document.querySelector('#explanation').value;answers=0;messages=[];mode='idle';special='';hint='';draft='';location.hash='conversation';break;
    case 'latest-message': {
      const history=document.querySelector('#chat-history');
      history?.scrollTo({top:history.scrollHeight,behavior:matchMedia('(prefers-reduced-motion: reduce)').matches?'instant':'smooth'});
      break;
    }
    case 'record':startRecord();break;
    case 'stop':finishRecording();break;
    case 'cancel-record':stopClock();mode='idle';draft='';render({keepScroll:true});break;
    case 'typed':mode='typed';draft='';render({keepScroll:true});document.querySelector('#answer-input').focus();break;
    case 'sample':document.querySelector('#answer-input').value=chosen.sample;break;
    case 'confirm':confirmAnswer();break;
    case 'hint':hint='hint';render({keepScroll:true});break;
    case 'rephrase':hint='rephrase';render({keepScroll:true});break;
    case 'close-help':hint='';render({keepScroll:true});break;
    case 'listen':case 'listen-slow':case 'play-preview':notify('Демо воспроизведения: в этом HTML-макете звук не включается.');break;
    case 'recover':{const previous=special;special='';render({keepScroll:true});if(previous==='interrupted'||previous==='uncertain')startRecord();else if(previous==='offline')notify('Демо: состояние сверено, можно продолжить с текущего вопроса.');break;}
    case 'finish':openModal(`<h2>Закончить разговор?</h2><p>Можно остановиться в любой момент. Покажем итог по ${answers} уже принятым ответам. Неотправленная запись не сохранится.</p><div class="actions">${btn('Продолжить','close-modal')}${btn('Закончить','finish-confirm','primary')}</div>`);break;
    case 'close-modal':document.querySelector('#modal').close();break;
    case 'finish-confirm':document.querySelector('#modal').close();complete();break;
    case 'delete-history':openModal(`<h2>Удалить разговор?</h2><p>Из демонстрационной истории исчезнет этот разговор и связанные замечания.</p><div class="actions">${btn('Отмена','close-modal')}${btn('Удалить','delete-confirm','primary')}</div>`);break;
    case 'delete-confirm':saved=null;messages=[];answers=0;document.querySelector('#modal').close();render();notify('Разговор удалён из демонстрационной истории.');break;
  }
});
document.querySelector('#mobile-toggle').addEventListener('click',event=>{const active=document.querySelector('#prototype-shell').classList.toggle('phone-preview');event.target.textContent=active?'Широкий экран':'Вид телефона';});
// В макете прерывание также не должно оставлять работающий таймер после ухода со страницы.
document.addEventListener('visibilitychange',()=>{if(document.hidden&&mode==='recording'){stopClock();mode='idle';special='interrupted';if(location.hash==='#conversation')render({keepScroll:true});}});
window.addEventListener('hashchange',()=>{stopClock();if(mode==='recording'){mode='idle';special='interrupted';}document.querySelector('#modal').close();render();});
hydrate();render();
