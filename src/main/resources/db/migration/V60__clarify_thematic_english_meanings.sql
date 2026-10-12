-- V59 уже применена пользователем: её checksum и прежние версии слов сохраняются.
-- Уточнения становятся отдельными опубликованными редакциями, снимки игр не меняются.
create table quiz_v60_meanings (
    code varchar(255) primary key,
    old_source varchar(512) not null,
    old_answer varchar(512) not null,
    old_note text not null,
    new_source varchar(512) not null,
    new_answer varchar(512) not null,
    new_note text not null
);
insert into quiz_v60_meanings values
('english-study-en-ru-v2', 'approach (method)', 'подход к решению задачи',
 'approach (method) — подход к решению задачи. Тема: Учёба и исследования.',
 'approach (to a problem)', 'подход к решению задачи',
 'approach (to a problem) — подход к решению задачи. Тема: Учёба и исследования.'),
('english-study-ru-en-v2', 'подход к решению задачи', 'approach',
 'approach (method) — подход к решению задачи. Тема: Учёба и исследования.',
 'подход к решению задачи', 'approach',
 'approach (to a problem) — подход к решению задачи. Тема: Учёба и исследования.'),
('english-communication-en-ru-v2', 'sensitive (to others'' feelings)', 'чуткий к чувствам других',
 'sensitive (to others'' feelings) — чуткий к чувствам других. Тема: Общение и эмоции.',
 'sensitive (to criticism)', 'легкоранимый',
 'sensitive (to criticism) — легкоранимый. Тема: Общение и эмоции.'),
('english-communication-ru-en-v2', 'чуткий к чувствам других', 'sensitive',
 'sensitive (to others'' feelings) — чуткий к чувствам других. Тема: Общение и эмоции.',
 'легкоранимый', 'sensitive',
 'sensitive (to criticism) — легкоранимый. Тема: Общение и эмоции.');

-- Обновляются только неизменённые системные слова V59. Черновики, архив и
-- редакции администратора имеют приоритет и не перезаписываются этой поставкой.
create table quiz_v60_items (item_id bigint primary key, old_version_id bigint not null, code varchar(255) not null);
insert into quiz_v60_items
select i.id,v.id,m.code from quiz_v60_meanings m
join vocabulary_sets s on s.code=m.code and s.status='PUBLISHED'
join vocabulary_items i on i.set_id=s.id and i.status='PUBLISHED' and i.draft_version_id is null
join vocabulary_item_versions v on v.id=i.published_version_id
where v.version_no=1 and v.status='PUBLISHED' and v.created_by is null and v.reviewed_by is null
  and v.source_text=m.old_source and v.source_normalized=m.old_source and v.meaning_note_ru=m.old_note
  and v.meaning_note_kk is null and v.pronunciation is null and v.universal_audience=true
  and v.difficulty=2 and v.source_reference in
      ('https://dictionary.cambridge.org/dictionary/english-russian/approach',
       'https://dictionary.cambridge.org/dictionary/english-russian/sensitive')
  and not exists(select 1 from vocabulary_item_versions other where other.item_id=i.id and other.version_no>1)
  and (select count(*) from vocabulary_item_answers a where a.item_version_id=v.id)=1
  and exists(select 1 from vocabulary_item_answers a where a.item_version_id=v.id
             and a.is_primary=true and a.answer_text=m.old_answer and a.answer_normalized=m.old_answer)
  and not exists(select 1 from vocabulary_item_grades g where g.item_version_id=v.id)
  and not exists(select 1 from vocabulary_distractor_exclusions e
                 where e.item_version_id=v.id or e.excluded_item_version_id=v.id);

insert into vocabulary_item_versions
    (item_id,version_no,status,source_text,source_normalized,part_of_speech,difficulty,meaning_note_ru,
     source_reference,universal_audience,reviewed_at,published_at)
select x.item_id,2,'PUBLISHED',m.new_source,m.new_source,v.part_of_speech,v.difficulty,m.new_note,
       v.source_reference,true,current_timestamp,current_timestamp
from quiz_v60_items x join quiz_v60_meanings m on m.code=x.code
join vocabulary_item_versions v on v.id=x.old_version_id;
insert into vocabulary_item_answers(item_version_id,answer_text,answer_normalized,is_primary,primary_marker)
select v.id,m.new_answer,m.new_answer,true,1 from quiz_v60_items x
join quiz_v60_meanings m on m.code=x.code
join vocabulary_item_versions v on v.item_id=x.item_id and v.version_no=2;
insert into quiz_content_editorial_events(content_type,content_id,version_id,from_status,to_status)
select 'VOCABULARY_ITEM',x.item_id,v.id,'DRAFT','PUBLISHED' from quiz_v60_items x
join vocabulary_item_versions v on v.item_id=x.item_id and v.version_no=2;
update vocabulary_items set published_version_id=
    (select v.id from vocabulary_item_versions v where v.item_id=vocabulary_items.id and v.version_no=2),
    updated_at=current_timestamp where id in(select item_id from quiz_v60_items);
drop table quiz_v60_items;
drop table quiz_v60_meanings;
