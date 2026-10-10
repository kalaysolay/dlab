/* Контрактные клиентские проверки: boolean/форматы, серверное время и однократные aria-пороги. */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const core = require('../src/main/resources/static/js/quiz-game-core.js');
test('исторический алфавит, trim и регистр; внутренние пробелы не исправляются',()=>{
    assert.equal(core.normalizeCode('  qz7k2m '),'QZ7K2M');
    for(const invalid of ['QZ 7K2M','QZ0000','QZIO11','QZABCDX','QZ*BCD']) assert.throws(()=>core.normalizeCode(invalid));
});
test('false является содержательным ответом, null не принимается',()=>{
    assert.equal(core.answer({contentKind:'FACT'},false),false);
    assert.equal(core.answer({contentKind:'FACT'},true),true);
    assert.throws(()=>core.answer({contentKind:'FACT'},null));
});
test('SCQ и перевод отправляют один выбранный id',()=>{
    for(const r of [{format:'SCQ',contentKind:'QUESTION'},{format:'SCQ',contentKind:'VOCABULARY'}]) {
        assert.deepEqual(core.answer(r,['B']),{selected:['B']}); assert.throws(()=>core.answer(r,[])); assert.throws(()=>core.answer(r,['A','B']));
    }
});
test('MCQ сохраняет весь выбор',()=>assert.deepEqual(core.answer({format:'MCQ'},['B','A']),{selected:['B','A']}));
test('MATCHING сохраняет идентификаторы, FILL_IN не нормализует введённый текст',()=>{
    assert.deepEqual(core.answer({format:'MATCHING'},{'1':'Один'}),{pairs:{'1':'Один'}});
    assert.deepEqual(core.answer({format:'FILL_IN'},{'[[1]]':' 30,0 '}),{answers:{'[[1]]':' 30,0 '}});
});
test('таймер считает серверное смещение и не даёт отрицательное время',()=>{
    assert.equal(core.remaining('2026-10-10T12:00:20Z',2000,Date.parse('2026-10-10T12:00:15Z')),3);
    assert.equal(core.remaining('2026-10-10T12:00:20Z',0,Date.parse('2026-10-10T12:00:20Z')),0);
});
test('пороги не повторяются при poll/reconnect и после восстановления sessionStorage',()=>{
    const seen=new Set(); assert.deepEqual(core.thresholds(11,10,seen),[10]); assert.deepEqual(core.thresholds(10,9,seen),[]);
    assert.deepEqual(core.thresholds(11,8,seen),[]); assert.deepEqual(core.thresholds(8,4,seen),[5]);
    assert.deepEqual(core.thresholds(Infinity,4,new Set([...seen])),[]);
});
test('boolean и null отображаются раздельно в личном разборе',()=>{
    const labels={true:'Верю',false:'Не верю',noAnswer:'Нет ответа'};
    assert.equal(core.answerText(false,[],labels),'Не верю'); assert.equal(core.answerText(null,[],labels),'Нет ответа');
});
test('разбор отображает подпись снимка, а не технический id',()=>{
    const options=[{id:'A',text:'Первый'},{id:'B',text:'Второй'}];
    assert.equal(core.answerText({selected:['B']},options,{}),'Второй'); assert.equal(core.answerText(['A','B'],options,{}),'Первый, Второй');
});
test('разбор MATCHING и FILL_IN не требует изменяемого банка',()=>{
    assert.equal(core.answerText({pairs:{a:'b'}},{left:[{id:'a',text:'Лево'}],right:[{id:'b',text:'Право'}]},{}),'Лево → Право');
    assert.equal(core.answerText({'[[1]]':'30'},{},{}),'[[1]]: 30');
});
