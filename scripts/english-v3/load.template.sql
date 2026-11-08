-- Выполнять внутри транзакции: psql -X -v ON_ERROR_STOP=1 --single-transaction -f 02_load.sql
-- Повторная загрузка отклоняется до изменения рабочих таблиц. Удаление v3 не выполняется.
create temporary table en3_banks(theme varchar(32) primary key,code varchar(96) unique,title_ru varchar(255),title_kk varchar(255),catalog_order integer) on commit drop;
insert into en3_banks values
/* BANK_VALUES */
create temporary table en3_guard(check_name text,valid boolean not null check(valid=true)) on commit drop;
insert into en3_guard select 'Новые коды v3 уже заняты: повторная загрузка запрещена',not exists(select 1 from vocabulary_sets s join en3_banks b on lower(s.code)=lower(b.code))
    and not exists(select 1 from quiz_games g join en3_banks b on lower(g.code)=lower(b.code));
create temporary table en3_words(word_no integer primary key,theme varchar(32),group_no integer,kind varchar(16),source_text varchar(512),source_normalized varchar(512),part_of_speech varchar(64),difficulty integer,note_ru text,answer_text varchar(512),answer_normalized varchar(512)) on commit drop;
insert into en3_words values
/* WORD_VALUES */
create temporary table en3_ambiguous(first_no integer,second_no integer,reason text) on commit drop;
insert into en3_ambiguous values
/* AMBIGUOUS_VALUES */

insert into vocabulary_sets(code,source_language,target_language,status)
select code,'en','ru','PUBLISHED' from en3_banks;
insert into vocabulary_set_versions(set_id,version_no,status,title_ru,title_kk,source_reference,cross_set_distractors_allowed,reviewed_at,published_at)
select s.id,1,'PUBLISHED',b.title_ru,b.title_kk,/* PROVENANCE */,false,current_timestamp,current_timestamp
from en3_banks b join vocabulary_sets s on s.code=b.code;
update vocabulary_sets set published_version_id=(select v.id from vocabulary_set_versions v where v.set_id=vocabulary_sets.id)
where code in(select code from en3_banks);

-- Порядок INSERT не используется для привязки слова к ID: каждому созданному item
-- назначаем номер через отдельную таблицу соответствий в пределах его нового набора.
insert into vocabulary_items(set_id,status)
select s.id,'PUBLISHED' from en3_words w join en3_banks b on b.theme=w.theme join vocabulary_sets s on s.code=b.code;
create temporary table en3_ids(item_id bigint primary key,word_no integer unique,group_no integer) on commit drop;
insert into en3_ids
select x.id,w.word_no,w.group_no from
    (select i.id,b.theme,row_number() over(partition by i.set_id order by i.id) as rn
     from vocabulary_items i join vocabulary_sets s on s.id=i.set_id join en3_banks b on b.code=s.code) x
join (select w.*,row_number() over(partition by theme order by word_no) as rn from en3_words w) w
on w.theme=x.theme and w.rn=x.rn;
insert into vocabulary_item_versions(item_id,version_no,status,source_text,source_normalized,part_of_speech,difficulty,meaning_note_ru,source_reference,universal_audience,reviewed_at,published_at)
select m.item_id,1,'PUBLISHED',w.source_text,w.source_normalized,w.part_of_speech,w.difficulty,w.note_ru,/* PROVENANCE */,true,current_timestamp,current_timestamp
from en3_ids m join en3_words w on w.word_no=m.word_no;
insert into vocabulary_item_answers(item_version_id,answer_text,answer_normalized,is_primary,primary_marker)
select v.id,w.answer_text,w.answer_normalized,true,1 from en3_ids m
join vocabulary_item_versions v on v.item_id=m.item_id join en3_words w on w.word_no=m.word_no;
update vocabulary_items set published_version_id=(select v.id from vocabulary_item_versions v where v.item_id=vocabulary_items.id)
where id in(select item_id from en3_ids);

-- Движок выбирает только из своей смысловой пятёрки. Запрет симметричен,
-- поэтому достаточно одной записи на пару. Синонимичные пары также исключаются.
insert into vocabulary_distractor_exclusions(item_version_id,excluded_item_version_id,reason)
select va.id,vb.id,'Разные смысловые группы: варианты должны проверять близкие значения.'
from en3_ids a join en3_ids b on a.word_no<b.word_no and a.group_no<>b.group_no
join en3_words wa on wa.word_no=a.word_no join en3_words wb on wb.word_no=b.word_no and wa.theme=wb.theme
join vocabulary_item_versions va on va.item_id=a.item_id join vocabulary_item_versions vb on vb.item_id=b.item_id;
insert into vocabulary_distractor_exclusions(item_version_id,excluded_item_version_id,reason)
select va.id,vb.id,e.reason from en3_ambiguous e join en3_ids a on a.word_no=e.first_no join en3_ids b on b.word_no=e.second_no
join vocabulary_item_versions va on va.item_id=a.item_id join vocabulary_item_versions vb on vb.item_id=b.item_id;

insert into quiz_games(code,status,catalog_visible) select code,'PUBLISHED',true from en3_banks;
insert into quiz_game_versions(game_id,version_no,game_type,status,title_ru,title_kk,description_ru,description_kk,mechanic_label_ru,mechanic_label_kk,card_style,catalog_order,audience_policy,grade_selection_mode,min_question_count,default_question_count,max_question_count,min_round_seconds,default_round_seconds,max_round_seconds,question_count_editable,round_seconds_editable,max_players,reviewed_at,published_at)
select g.id,1,'WORD_TRANSLATION','PUBLISHED',b.title_ru,b.title_kk,
    '50 заданий: 35 слов и 15 выражений. Близкие по смыслу варианты перевода.',
    '50 тапсырма: 35 сөз және 15 тіркес. Мағынасы жақын аударма нұсқалары.',
    'Выберите перевод','Аударманы таңдаңыз','english',b.catalog_order,'UNIVERSAL','NONE',5,10,30,5,20,60,true,true,4,current_timestamp,current_timestamp
from en3_banks b join quiz_games g on g.code=b.code;
insert into quiz_word_game_configs(game_version_id,vocabulary_set_id,source_language,target_language)
select v.id,s.id,'en','ru' from en3_banks b join quiz_games g on g.code=b.code
join quiz_game_versions v on v.game_id=g.id join vocabulary_sets s on s.code=b.code;
update quiz_games set published_version_id=(select v.id from quiz_game_versions v where v.game_id=quiz_games.id),admin_revision=1
where code in(select code from en3_banks);
insert into quiz_content_editorial_events(content_type,content_id,version_id,from_status,to_status)
select 'VOCABULARY_SET',s.id,s.published_version_id,'DRAFT','PUBLISHED' from vocabulary_sets s join en3_banks b on b.code=s.code;
insert into quiz_content_editorial_events(content_type,content_id,version_id,from_status,to_status)
select 'VOCABULARY_ITEM',i.id,i.published_version_id,'DRAFT','PUBLISHED' from en3_ids m join vocabulary_items i on i.id=m.item_id;
insert into quiz_content_editorial_events(content_type,content_id,version_id,from_status,to_status)
select 'GAME',g.id,g.published_version_id,'DRAFT','PUBLISHED' from quiz_games g join en3_banks b on b.code=g.code;
