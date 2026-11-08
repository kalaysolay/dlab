// Воспроизводимая сборка авторского набора и SQL для существующей схемы Damulab.
// Рабочую БД генератор не открывает. Все ID назначает БД при отдельном запуске SQL.
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const root = __dirname;
const themes = [], groups = [], rows = [];
let theme, group;
for (const line of fs.readFileSync(path.join(root, 'content.txt'), 'utf8').split(/\r?\n/).map(s => s.trim()).filter(Boolean)) {
  if (line.startsWith('@ ')) {
    const [code, ru, kk] = line.slice(2).split('|');
    theme = { code, ru, kk, order: themes.length + 1 }; themes.push(theme);
  } else if (line.startsWith('# ')) {
    const [kind, pos, label] = line.slice(2).split('|');
    group = { id: groups.length + 1, theme: theme.code, kind, pos, label, rows: [] }; groups.push(group);
  } else {
    const [english, russian, prompt = english] = line.split('|');
    const row = { id: rows.length + 1, theme: theme.code, group: group.id, kind: group.kind, pos: group.pos, english, russian, prompt };
    assert(english && russian && prompt && prompt.length <= 512 && russian.length <= 512);
    assert.equal(english.includes(' '), group.kind === 'expression', `Тип единицы: ${english}`);
    rows.push(row); group.rows.push(row);
  }
}
assert.equal(themes.length, 10); assert.equal(rows.length, 500); assert.equal(groups.length, 100);
assert.equal(new Set(rows.map(r => r.prompt.toLowerCase())).size, 500, 'Повтор английского вопроса');
assert.equal(new Set(rows.map(r => r.english.toLowerCase())).size, 500, 'Повтор слова или выражения');
assert.equal(rows.filter(r => r.kind === 'word').length, 350);
for (const t of themes) {
  assert.equal(rows.filter(r => r.theme === t.code).length, 50);
  assert.equal(rows.filter(r => r.theme === t.code && r.kind === 'word').length, 35);
}
for (const g of groups) {
  assert.equal(g.rows.length, 5, g.label);
  assert.equal(new Set(g.rows.map(r => r.russian.toLowerCase())).size, 5, g.label);
}
const pairKey = (a, b) => [a, b].sort((x, y) => x - y).join(':');
const ambiguous = new Map();
for (const line of fs.readFileSync(path.join(root, 'ambiguous-pairs.txt'), 'utf8').split(/\r?\n/).filter(s => s && !s.startsWith('#'))) {
  const [a, b, reason] = line.split('|');
  const first = rows.find(r => r.prompt === a), second = rows.find(r => r.prompt === b);
  assert(first && second && first.group === second.group && reason, line);
  ambiguous.set(pairKey(first.id, second.id), reason);
}
const candidates = r => rows.filter(c => c.group === r.group && c.id !== r.id && !ambiguous.has(pairKey(r.id, c.id)));
rows.forEach(r => assert(candidates(r).length >= 3, `Недостаточно безопасных вариантов: ${r.prompt}`));
const q = s => s === null ? 'NULL' : "'" + String(s).replaceAll("'", "''") + "'";
const norm = s => s.normalize('NFKC').toLowerCase().replace(/\s+/g, ' ').trim();
const values = list => list.map(row => '(' + row.map(v => typeof v === 'number' ? v : q(v)).join(',') + ')').join(',\n') + ';\n';
const write = (name, content) => fs.writeFileSync(path.join(root, name), content, 'utf8');
const provenance = 'Авторский набор Damulab EN→RU v3: 350 слов и 150 выражений; scripts/english-v3/content.txt';
const header = '-- Сгенерировано: node scripts/english-v3/generate.cjs. Ручные исправления вносить в исходник.\n';
let load = header + fs.readFileSync(path.join(root, 'load.template.sql'), 'utf8');
load = load.replace('/* BANK_VALUES */', values(themes.map(t => [t.code, `english-${t.code}-en-ru-v3`, `Английский: ${t.ru} · EN → RU`, `Ағылшын тілі: ${t.kk} · EN → RU`, 100 + t.order])));
load = load.replace('/* WORD_VALUES */', values(rows.map(r => [r.id, r.theme, r.group, r.kind, r.prompt, norm(r.prompt), r.pos, 3, `${r.english} — ${r.russian}.`, r.russian, norm(r.russian)])));
load = load.replace('/* AMBIGUOUS_VALUES */', values([...ambiguous].map(([key, reason]) => [...key.split(':').map(Number), reason])));
load = load.replaceAll('/* PROVENANCE */', q(provenance));
write('02_load.sql', load);
let verify = header + fs.readFileSync(path.join(root, 'verify.template.sql'), 'utf8');
verify = verify.replace('/* EXPECTED_VALUES */', values(rows.map(r => [r.id, `english-${r.theme}-en-ru-v3`, r.group, r.kind, r.prompt, r.russian])));
verify = verify.replace('/* AMBIGUOUS_VALUES */', values([...ambiguous].map(([key, reason]) => [...key.split(':').map(Number), reason])));
write('03_verify.sql', verify);
const oldEn = ['basic-en-ru-400-v1', ...['study','travel','communication','decisions','environment','health','work','technology'].map(t => `english-${t}-en-ru-v2`)];
const oldReverse = ['basic-ru-en-300-v1', ...['study','travel','communication','decisions','environment','health'].map(t => `english-${t}-ru-en-v2`)];
for (const [name, codes] of [['01_delete.sql', oldEn], ['01_delete_all_english.sql', [...oldEn, ...oldReverse]]]) {
  write(name, header + fs.readFileSync(path.join(root, 'delete.template.sql'), 'utf8').replace('/* OLD_CODES */', values(codes.map(c => [c]))));
}
write('00_preview.sql', header + `-- Только чтение. Показывает оба варианта области удаления; никаких изменений БД.\n` +
  `with old_codes(code,direction) as (values\n${[...oldEn.map(c => [c,'en-ru']),...oldReverse.map(c => [c,'ru-en'])].map(r => '('+r.map(q).join(',')+')').join(',\n')}\n)\n` +
  `select c.direction,c.code,s.id as set_id,g.id as game_id,\n` +
  `  (select count(*) from vocabulary_items i where i.set_id=s.id) as items,\n` +
  `  (select count(*) from quiz_rooms r where r.game_id=g.id) as linked_rooms,\n` +
  `  (select count(*) from quiz_rooms r where r.game_id=g.id and r.status in('ACTIVE','WAITING')) as unfinished_rooms,\n` +
  `  (select count(*) from quiz_word_game_configs x join quiz_game_versions v on v.id=x.game_version_id where x.vocabulary_set_id=s.id and v.game_id<>g.id) as other_game_versions\n` +
  `from old_codes c left join vocabulary_sets s on s.code=c.code left join quiz_games g on g.code=c.code order by c.direction,c.code;\n`);
for (const [name, deletion] of [['replace-en-ru.psql', '01_delete.sql'], ['replace-all-english.psql', '01_delete_all_english.sql']]) {
  write(name, `\\set ON_ERROR_STOP on\nBEGIN;\nSET LOCAL lock_timeout = '10s';\nSET LOCAL statement_timeout = '120s';\n\\ir ${deletion}\n\\ir 02_load.sql\n\\ir 03_verify.sql\nCOMMIT;\n`);
}
// Предпросмотр имеет ровно по 125 правильных ответов в каждой позиции. В самой игре
// движок независимо перемешивает ответы и выбирает три из четырёх разрешённых соседей.
const preview = rows.map(r => {
  const pool = candidates(r), wrong = pool.slice(r.id % pool.length).concat(pool.slice(0, r.id % pool.length)).slice(0, 3);
  const options = wrong.map(c => c.russian); options.splice((r.id - 1) % 4, 0, r.russian);
  return [r.id, r.theme, r.kind, r.group, r.prompt, ...options, 'ABCD'[(r.id - 1) % 4]];
});
write('questions.tsv', ['id\ttheme\tkind\tgroup\tprompt\tA\tB\tC\tD\tcorrect', ...preview.map(r => r.join('\t'))].join('\n') + '\n');
write('manifest.json', JSON.stringify({ total: rows.length, words: 350, expressions: 150, themes: themes.map(t => ({...t, items: 50, words: 35, expressions: 15})), groups: groups.length, ambiguousPairs: ambiguous.size, minimumDistractors: Math.min(...rows.map(r => candidates(r).length)), source: provenance }, null, 2) + '\n');
console.log(`Готово: ${rows.length} заданий, 350 слов / 150 выражений, ${groups.length} смысловых групп; минимум ${Math.min(...rows.map(r => candidates(r).length))} дистрактора.`);
