// Статические примеры для сравнения оформления; данные взяты из банка «Жители океана».
const rows = [
  {n:1,topic:'Акулы',prompt:'Скелет акулы состоит из костей.',state:'wrong',answer:'Верю',right:'Не верю',explanation:'Нет. Акулы относятся к хрящевым рыбам: их скелет образован хрящом.',source:'https://www.fisheries.noaa.gov/fish-sharks',publisher:'NOAA'},
  {n:2,topic:'Осьминоги',prompt:'У осьминога три сердца.',state:'correct',answer:'Верю',right:'Верю',explanation:'Да. Два сердца направляют кровь к жабрам, а третье — к остальным частям тела.',source:'https://ocean.si.edu/ocean-life/invertebrates/octopuses-squids-and-relatives',publisher:'Smithsonian Ocean'},
  {n:3,topic:'Дельфины',prompt:'Дельфины дышат жабрами.',state:'correct',answer:'Не верю',right:'Не верю',explanation:'Нет. Дельфины дышат воздухом с помощью лёгких. Поэтому им нужно подниматься к поверхности воды.',source:'https://oceanservice.noaa.gov/facts/dolphin.html',publisher:'NOAA'},
  {n:4,topic:'Киты',prompt:'Синий кит — самое крупное животное на Земле.',state:'timeout',answer:'Время вышло',right:'Верю',explanation:'Да. Синий кит — самое крупное животное на Земле. Питается он главным образом мелкими рачками — крилем.',source:'https://www.fisheries.noaa.gov/species/blue-whale',publisher:'NOAA'},
  {n:5,topic:'Осьминоги',prompt:'У осьминога десять рук.',state:'correct',answer:'Не верю',right:'Не верю',explanation:'Нет. У осьминога восемь рук. У кальмара восемь рук и ещё два длинных ловчих щупальца.',source:'https://ocean.si.edu/ocean-life/invertebrates/octopuses-squids-and-relatives',publisher:'Smithsonian Ocean'}
];
const labels = {wrong:'Неверно',correct:'Верно',timeout:'Без ответа'};
const directions = {cards:'01 / Карточки — ответ, исправление и объяснение видны сразу. Удобно прочитать весь разбор.',list:'02 / Компактный список — общий результат на одном экране. Раскройте вопрос, чтобы увидеть детали.',focus:'03 / По одному вопросу — крупный текст и спокойный темп. Удобно внимательно разобрать ошибки.'};
let variant='cards', filter='all', selected=1;
const content=document.querySelector('#review-content');
const badge=r=>`<span class="badge ${r.state}">${labels[r.state]}</span>`;
const detail=r=>`<div class="answers"><div class="answer ${r.state}"><small>Ваш ответ</small><strong>${r.answer}</strong></div><div class="answer correct"><small>Правильный ответ</small><strong>${r.right}</strong></div></div><div class="explanation"><span class="label"><svg class="icon" aria-hidden="true"><use href="../../../src/main/resources/static/icons/lucide-sprite.svg#book-open-check"></use></svg>Почему так</span><p>${r.explanation}</p><a class="source" href="${r.source}" target="_blank" rel="noopener noreferrer">Источник: ${r.publisher} ↗</a></div>`;
// В карточках верный ответ не повторяем; остальные варианты оставлены для сравнения.
const compactDetail=r=>`<div class="answer-line"><span>Ваш ответ: <strong class="answer-value ${r.state}">${r.answer}</strong></span>${r.state==='correct'?'':`<span>Правильный ответ: <strong class="answer-value correct">${r.right}</strong></span>`}</div><div class="explanation"><span class="label">Почему так</span><p>${r.explanation}</p></div>`;
const card=(r,focus=false)=>`<article class="card ${focus?'focus-card':'compact-card'}"><div class="card-top"><span class="number">${String(r.n).padStart(2,'0')}</span>${focus?badge(r):''}<h3>${r.prompt}</h3>${focus?'':badge(r)}</div>${focus?detail(r):compactDetail(r)}</article>`;
function render(){
  const visible=rows.filter(r=>filter==='all'||r.state===filter);
  // При смене фильтра показываем первый подходящий вопрос вместо пустой карточки.
  if(!visible.some(r=>r.n===selected)) selected=visible[0].n;
  document.querySelector('#direction').textContent=directions[variant];
  document.querySelector('#preview').setAttribute('aria-labelledby',`tab-${variant}`);
  document.querySelectorAll('[data-variant]').forEach(b=>{const active=b.dataset.variant===variant;b.setAttribute('aria-selected',active);b.tabIndex=active?0:-1;});
  document.querySelectorAll('[data-filter]').forEach(b=>b.setAttribute('aria-pressed',b.dataset.filter===filter));
  if(variant==='cards') content.innerHTML=`<div class="review-stack">${visible.map(r=>card(r)).join('')}</div>`;
  if(variant==='list') content.innerHTML=`<div class="compact">${visible.map((r,i)=>`<details ${i===0?'open':''}><summary><span class="number">${String(r.n).padStart(2,'0')}</span><span class="prompt">${r.prompt}</span>${badge(r)}<span class="toggle" aria-hidden="true">+</span></summary><div class="detail-body">${detail(r)}</div></details>`).join('')}</div>`;
  if(variant==='focus'){
    const index=visible.findIndex(r=>r.n===selected), r=visible[index];
    content.innerHTML=`<div class="focus-layout"><nav class="navigator" aria-label="Вопросы разбора"><p>Вопросы · ${visible.length}</p>${visible.map(item=>`<button data-question="${item.n}" aria-current="${item.n===selected}" aria-label="Вопрос ${item.n}. ${item.topic}. ${labels[item.state]}"><span>${String(item.n).padStart(2,'0')}</span><span class="nav-copy">${item.topic}<small>${labels[item.state]}</small></span></button>`).join('')}</nav><div>${card(r,true)}<div class="pager"><button data-step="-1" ${index===0?'disabled':''}>Назад</button><span>${index+1} из ${visible.length}</span><button data-step="1" ${index===visible.length-1?'disabled':''}>Следующий вопрос</button></div></div></div>`;
  }
}
document.querySelectorAll('[data-variant]').forEach(b=>{
  b.addEventListener('click',()=>{variant=b.dataset.variant;render();});
  b.addEventListener('keydown',e=>{
    const tabs=[...document.querySelectorAll('[data-variant]')];let index=tabs.indexOf(b);
    if(e.key==='ArrowRight') index=(index+1)%tabs.length; else if(e.key==='ArrowLeft') index=(index+tabs.length-1)%tabs.length; else if(e.key==='Home') index=0; else if(e.key==='End') index=tabs.length-1; else return;
    e.preventDefault();tabs[index].click();tabs[index].focus();
  });
});
document.querySelectorAll('[data-filter]').forEach(b=>b.addEventListener('click',()=>{filter=b.dataset.filter;render();}));
content.addEventListener('click',e=>{
  const question=e.target.closest('[data-question]'),step=e.target.closest('[data-step]');
  if(question){selected=Number(question.dataset.question);render();content.querySelector(`[data-question="${selected}"]`).focus();}
  if(step){const visible=rows.filter(r=>filter==='all'||r.state===filter);selected=visible[visible.findIndex(r=>r.n===selected)+Number(step.dataset.step)].n;render();content.querySelector('.focus-card h3').tabIndex=-1;content.querySelector('.focus-card h3').focus();}
});
document.querySelector('#reset').addEventListener('click',()=>{filter='all';selected=1;render();document.querySelector('[data-filter="all"]').focus();});
render();
