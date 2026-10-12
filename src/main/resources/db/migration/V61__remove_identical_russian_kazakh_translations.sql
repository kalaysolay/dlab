-- Убираем пары ru↔kk, где текущий основной перевод совпадает с исходным словом.
-- Сравнение игнорирует регистр и пробелы по краям; остальные языковые пары не затрагиваются.
create table quiz_v61_items (item_id bigint primary key, has_rounds boolean not null);
insert into quiz_v61_items
select i.id, exists (
    select 1 from quiz_rounds r join vocabulary_item_versions h on h.id=r.vocabulary_item_version_id
    where h.item_id=i.id
)
from vocabulary_items i
join vocabulary_sets s on s.id=i.set_id
join vocabulary_item_versions v on v.id=coalesce(i.draft_version_id,i.published_version_id)
join vocabulary_item_answers a on a.item_version_id=v.id and a.is_primary=true
where ((s.source_language='ru' and s.target_language='kk')
    or (s.source_language='kk' and s.target_language='ru'))
  and lower(trim(v.source_text))=lower(trim(a.answer_text));

-- Использованные в играх версии защищены FK и нужны для истории результатов.
-- Архив исключает слово из новых раундов, сохраняя старые снимки и переводы.
insert into quiz_content_editorial_events(content_type,content_id,version_id,from_status,to_status)
select 'VOCABULARY_ITEM',i.id,i.published_version_id,i.status,'ARCHIVED'
from vocabulary_items i join quiz_v61_items x on x.item_id=i.id
where x.has_rounds=true and i.status<>'ARCHIVED';
update vocabulary_items set status='ARCHIVED',updated_at=current_timestamp
where id in (select item_id from quiz_v61_items where has_rounds=true);

-- Неиспользованные значения удаляем со всеми редакциями и зависимостями.
-- Журнал редакционных действий остаётся как история выполненных операций.
delete from vocabulary_distractor_exclusions
where item_version_id in (select v.id from vocabulary_item_versions v join quiz_v61_items x on x.item_id=v.item_id where x.has_rounds=false)
   or excluded_item_version_id in (select v.id from vocabulary_item_versions v join quiz_v61_items x on x.item_id=v.item_id where x.has_rounds=false);
delete from vocabulary_item_grades
where item_version_id in (select v.id from vocabulary_item_versions v join quiz_v61_items x on x.item_id=v.item_id where x.has_rounds=false);
delete from vocabulary_item_answers
where item_version_id in (select v.id from vocabulary_item_versions v join quiz_v61_items x on x.item_id=v.item_id where x.has_rounds=false);
update vocabulary_items set published_version_id=null,draft_version_id=null
where id in (select item_id from quiz_v61_items where has_rounds=false);
delete from vocabulary_item_versions where item_id in (select item_id from quiz_v61_items where has_rounds=false);
delete from vocabulary_items where id in (select item_id from quiz_v61_items where has_rounds=false);
drop table quiz_v61_items;
