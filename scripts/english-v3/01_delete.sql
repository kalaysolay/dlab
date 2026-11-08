-- Сгенерировано: node scripts/english-v3/generate.cjs. Ручные исправления вносить в исходник.
-- Физически удаляет только перечисленные старые банки, их игры и зависимую историю.
-- Запускать в транзакции. Защита от активных комнат и сторонних конфигураций — до DELETE.
lock table quiz_games,quiz_game_versions,quiz_word_game_configs,vocabulary_sets,vocabulary_set_versions,
    vocabulary_items,vocabulary_item_versions,vocabulary_item_answers,vocabulary_item_grades,
    vocabulary_distractor_exclusions,quiz_rooms,quiz_rounds,quiz_participants,quiz_answers,quiz_results,
    quiz_room_creation_keys,quiz_room_audience_grades,quiz_game_version_grades,quiz_content_editorial_events,
    quiz_game_visibility_events in share row exclusive mode;
create temporary table en3_old_codes(code varchar(96) primary key) on commit drop;
insert into en3_old_codes values
('basic-en-ru-400-v1'),
('english-study-en-ru-v2'),
('english-travel-en-ru-v2'),
('english-communication-en-ru-v2'),
('english-decisions-en-ru-v2'),
('english-environment-en-ru-v2'),
('english-health-en-ru-v2'),
('english-work-en-ru-v2'),
('english-technology-en-ru-v2');

create temporary table en3_old_sets on commit drop as select id from vocabulary_sets where code in(select code from en3_old_codes);
create temporary table en3_old_items on commit drop as select id from vocabulary_items where set_id in(select id from en3_old_sets);
create temporary table en3_old_item_versions on commit drop as select id from vocabulary_item_versions where item_id in(select id from en3_old_items);
create temporary table en3_old_set_versions on commit drop as select id from vocabulary_set_versions where set_id in(select id from en3_old_sets);
create temporary table en3_old_games on commit drop as select id from quiz_games where code in(select code from en3_old_codes);
create temporary table en3_old_game_versions on commit drop as select id from quiz_game_versions where game_id in(select id from en3_old_games);
create temporary table en3_old_rooms on commit drop as
select id from quiz_rooms where game_id in(select id from en3_old_games) or game_version_id in(select id from en3_old_game_versions)
or id in(select room_id from quiz_rounds where vocabulary_item_version_id in(select id from en3_old_item_versions)
    or vocabulary_set_version_id in(select id from en3_old_set_versions));
create temporary table en3_delete_guard(check_name text,valid boolean not null check(valid=true)) on commit drop;
-- Общий банк, подключённый к чужой игре, нельзя молча удалить вместе с этой игрой.
insert into en3_delete_guard select 'Сторонняя игра использует удаляемый банк',not exists(select 1 from quiz_word_game_configs
    where vocabulary_set_id in(select id from en3_old_sets) and game_version_id not in(select id from en3_old_game_versions));
-- Даже после остановки приложения незавершённые комнаты требуют явного завершения.
insert into en3_delete_guard select 'Завершите ACTIVE и WAITING комнаты старых игр',not exists(select 1 from quiz_rooms
    where id in(select id from en3_old_rooms) and status in('ACTIVE','WAITING'));

delete from quiz_answers where round_id in(select id from quiz_rounds where room_id in(select id from en3_old_rooms))
    or participant_id in(select id from quiz_participants where room_id in(select id from en3_old_rooms));
delete from quiz_results where room_id in(select id from en3_old_rooms)
    or participant_id in(select id from quiz_participants where room_id in(select id from en3_old_rooms));
delete from quiz_room_creation_keys where room_id in(select id from en3_old_rooms);
delete from quiz_room_audience_grades where room_id in(select id from en3_old_rooms);
delete from quiz_rounds where room_id in(select id from en3_old_rooms);
delete from quiz_participants where room_id in(select id from en3_old_rooms);
delete from quiz_rooms where id in(select id from en3_old_rooms);
delete from quiz_game_visibility_events where game_id in(select id from en3_old_games);
delete from quiz_content_editorial_events where
    (content_type='GAME' and content_id in(select id from en3_old_games)) or
    (content_type='VOCABULARY_SET' and content_id in(select id from en3_old_sets)) or
    (content_type='VOCABULARY_ITEM' and content_id in(select id from en3_old_items));
delete from quiz_game_version_grades where game_version_id in(select id from en3_old_game_versions);
delete from quiz_word_game_configs where game_version_id in(select id from en3_old_game_versions);
update quiz_games set published_version_id=null,draft_version_id=null where id in(select id from en3_old_games);
delete from quiz_game_versions where id in(select id from en3_old_game_versions);
delete from quiz_games where id in(select id from en3_old_games);
delete from vocabulary_distractor_exclusions where item_version_id in(select id from en3_old_item_versions)
    or excluded_item_version_id in(select id from en3_old_item_versions);
delete from vocabulary_item_grades where item_version_id in(select id from en3_old_item_versions);
delete from vocabulary_item_answers where item_version_id in(select id from en3_old_item_versions);
update vocabulary_items set published_version_id=null,draft_version_id=null where id in(select id from en3_old_items);
delete from vocabulary_item_versions where id in(select id from en3_old_item_versions);
delete from vocabulary_items where id in(select id from en3_old_items);
update vocabulary_sets set published_version_id=null,draft_version_id=null where id in(select id from en3_old_sets);
delete from vocabulary_set_versions where id in(select id from en3_old_set_versions);
delete from vocabulary_sets where id in(select id from en3_old_sets);
