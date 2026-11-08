-- Сгенерировано: node scripts/english-v3/generate.cjs. Ручные исправления вносить в исходник.
-- Только чтение. Показывает оба варианта области удаления; никаких изменений БД.
with old_codes(code,direction) as (values
('basic-en-ru-400-v1','en-ru'),
('english-study-en-ru-v2','en-ru'),
('english-travel-en-ru-v2','en-ru'),
('english-communication-en-ru-v2','en-ru'),
('english-decisions-en-ru-v2','en-ru'),
('english-environment-en-ru-v2','en-ru'),
('english-health-en-ru-v2','en-ru'),
('english-work-en-ru-v2','en-ru'),
('english-technology-en-ru-v2','en-ru'),
('basic-ru-en-300-v1','ru-en'),
('english-study-ru-en-v2','ru-en'),
('english-travel-ru-en-v2','ru-en'),
('english-communication-ru-en-v2','ru-en'),
('english-decisions-ru-en-v2','ru-en'),
('english-environment-ru-en-v2','ru-en'),
('english-health-ru-en-v2','ru-en')
)
select c.direction,c.code,s.id as set_id,g.id as game_id,
  (select count(*) from vocabulary_items i where i.set_id=s.id) as items,
  (select count(*) from quiz_rooms r where r.game_id=g.id) as linked_rooms,
  (select count(*) from quiz_rooms r where r.game_id=g.id and r.status in('ACTIVE','WAITING')) as unfinished_rooms,
  (select count(*) from quiz_word_game_configs x join quiz_game_versions v on v.id=x.game_version_id where x.vocabulary_set_id=s.id and v.game_id<>g.id) as other_game_versions
from old_codes c left join vocabulary_sets s on s.code=c.code left join quiz_games g on g.code=c.code order by c.direction,c.code;
