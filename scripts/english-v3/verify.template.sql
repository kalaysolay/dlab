-- Проверка работает как внутри замены, так и отдельно с --single-transaction.
-- Ошибка любой проверки прерывает транзакцию, включая предшествующее удаление.
create temporary table en3_expected(word_no integer primary key,code varchar(96),group_no integer,kind varchar(16),prompt varchar(512),answer varchar(512)) on commit drop;
insert into en3_expected values
/* EXPECTED_VALUES */
create temporary table en3_expected_ambiguous(a integer,b integer,reason text) on commit drop;
insert into en3_expected_ambiguous values
/* AMBIGUOUS_VALUES */
create temporary table en3_verify_guard(check_name text,valid boolean not null check(valid=true)) on commit drop;
create temporary table en3_actual on commit drop as
select e.*,s.id as set_id,i.id as item_id,v.id as version_id from en3_expected e
join vocabulary_sets s on s.code=e.code join vocabulary_items i on i.set_id=s.id
join vocabulary_item_versions v on v.id=i.published_version_id and v.source_text=e.prompt
join vocabulary_item_answers a on a.item_version_id=v.id and a.is_primary=true and a.answer_text=e.answer
where s.status='PUBLISHED' and i.status='PUBLISHED' and v.status='PUBLISHED' and v.universal_audience=true;
insert into en3_verify_guard select '500 опубликованных вопросов и точные переводы',
    (select count(*) from en3_actual)=500 and (select count(distinct word_no) from en3_actual)=500
    and (select count(distinct item_id) from en3_actual)=500 and (select count(distinct version_id) from en3_actual)=500;
insert into en3_verify_guard select 'Ровно 500 items и 10 наборов',
    (select count(*) from vocabulary_items where set_id in(select set_id from en3_actual))=500
    and (select count(distinct set_id) from en3_actual)=10;
insert into en3_verify_guard select 'По 35 слов и 15 выражений в каждой теме',not exists(
    select code from en3_actual group by code having count(*)<>50 or sum(case when kind='word' then 1 else 0 end)<>35
    or sum(case when kind='expression' then 1 else 0 end)<>15);
insert into en3_verify_guard select 'Полные опубликованные наборы EN-RU без cross-set',not exists(
    select 1 from vocabulary_sets s left join vocabulary_set_versions v on v.id=s.published_version_id
    where s.id in(select set_id from en3_actual) and (s.source_language<>'en' or s.target_language<>'ru'
    or v.id is null or v.status<>'PUBLISHED' or v.cross_set_distractors_allowed=true));
insert into en3_verify_guard select 'Ровно один primary и нет лишних переводов',
    (select count(*) from vocabulary_item_answers where item_version_id in(select version_id from en3_actual))=500;
insert into en3_verify_guard select '10 видимых игр с верными конфигурациями',
    (select count(*) from quiz_games g join quiz_game_versions v on v.id=g.published_version_id
     join quiz_word_game_configs c on c.game_version_id=v.id join vocabulary_sets s on s.id=c.vocabulary_set_id
     where g.code in(select code from en3_expected) and s.code=g.code and c.source_language='en' and c.target_language='ru'
     and g.status='PUBLISHED' and g.catalog_visible=true and v.status='PUBLISHED' and v.game_type='WORD_TRANSLATION'
     and v.audience_policy='UNIVERSAL' and v.default_question_count=10 and v.max_question_count=30)=10;
-- Проверяется вся матрица пар: никакой посторонний вариант не должен попасть в раунд,
-- а близкие разрешённые переводы не должны исчезнуть из-за случайного запрета.
insert into en3_verify_guard select 'Точная матрица запретов по смысловым группам',not exists(
    select 1 from en3_actual a join en3_actual b on a.code=b.code and a.word_no<b.word_no
    where (a.group_no<>b.group_no or exists(select 1 from en3_expected_ambiguous p
        where p.a=a.word_no and p.b=b.word_no))
    <> exists(select 1 from vocabulary_distractor_exclusions x
        where (x.item_version_id=a.version_id and x.excluded_item_version_id=b.version_id)
           or (x.item_version_id=b.version_id and x.excluded_item_version_id=a.version_id)));
insert into en3_verify_guard select 'Не менее трёх безопасных дистракторов для каждого слова',not exists(
    select a.word_no from en3_actual a left join en3_actual b on a.code=b.code and a.word_no<>b.word_no
    and a.answer<>b.answer and not exists(select 1 from vocabulary_distractor_exclusions x
        where (x.item_version_id=a.version_id and x.excluded_item_version_id=b.version_id)
           or (x.item_version_id=b.version_id and x.excluded_item_version_id=a.version_id))
    group by a.word_no having count(b.word_no)<3);
select check_name,'OK' as result from en3_verify_guard;
select code,count(*) as questions,sum(case when kind='word' then 1 else 0 end) as words,
    sum(case when kind='expression' then 1 else 0 end) as expressions from en3_actual group by code order by code;
