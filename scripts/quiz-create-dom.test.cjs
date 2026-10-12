/* Настоящий DOM формы: зависимые классы и доступное количество вопросов без подключения к БД. */
const {test}=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path');
const {JSDOM}=require('./quiz-ui/node_modules/jsdom');
const script=fs.readFileSync(path.join(__dirname,'../src/main/resources/static/js/quiz-create.js'),'utf8');
function form(selectedSubject='',selectedGrade='') {
    const dom=new JSDOM(`<form data-student-quiz-create>
      <select name="subjectId"><option value="">Предмет</option><option value="1">Математика</option><option value="2">Физика</option></select>
      <select name="gradeId"><option value="">Класс</option>
        <option value="4" data-subject="1" data-count="16">4</option><option value="5" data-subject="1" data-count="8">5</option>
        <option value="5" data-subject="2" data-count="20">5</option></select>
      <select name="language"><option value="ru">Русский</option><option value="kk" selected>Қазақша</option></select>
      <input type="number" name="questionCount" value="15"><input name="roundSeconds" value="10">
      <p id="quiz-available" data-label="Доступно вопросов: {0}"></p></form>`,{runScripts:'outside-only'});
    const w=dom.window, f=w.document.querySelector('form');
    f.elements.subjectId.value=selectedSubject; f.elements.gradeId.value=selectedGrade;
    w.eval(script);
    return {w,f,change:(name,value)=>{f.elements[name].value=value;f.elements[name].dispatchEvent(new w.Event('change'));},close:()=>w.close()};
}
test('до выбора предмета класс недоступен; язык и значения по умолчанию не меняются',()=>{
    const h=form();try{
        assert.equal(h.f.elements.gradeId.disabled,true);
        assert.equal(h.f.elements.language.value,'kk');assert.equal(h.f.elements.questionCount.value,'15');assert.equal(h.f.elements.roundSeconds.value,'10');
    }finally{h.close();}
});
test('классы зависят от предмета, общий класс сохраняется, несовместимый сбрасывается',()=>{
    const h=form('1','4');try{
        assert.deepEqual(Array.from(h.f.elements.gradeId.options).map(o=>o.value),['','4','5']);
        h.change('subjectId','2'); assert.equal(h.f.elements.gradeId.value,'');
        h.change('gradeId','5');h.change('subjectId','1');assert.equal(h.f.elements.gradeId.value,'5');
    }finally{h.close();}
});
test('ошибка количества снимается при уменьшении или смене класса',()=>{
    const h=form('1','5');try{
        const count=h.f.elements.questionCount;
        assert.equal(count.validity.customError,true);assert.equal(h.w.document.querySelector('#quiz-available').textContent,'Доступно вопросов: 8');
        count.value='8';count.dispatchEvent(new h.w.Event('input'));assert.equal(count.validity.customError,false);
        count.value='15';h.change('gradeId','4');assert.equal(count.validity.customError,false);
    }finally{h.close();}
});
test('выбранные сервером значения сохраняются после отображения ошибки',()=>{
    const h=form('2','5');try{
        assert.equal(h.f.elements.subjectId.value,'2');assert.equal(h.f.elements.gradeId.value,'5');
        assert.equal(h.w.document.querySelector('#quiz-available').textContent,'Доступно вопросов: 20');
    }finally{h.close();}
});
