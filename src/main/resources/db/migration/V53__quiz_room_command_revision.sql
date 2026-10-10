-- Техническая optimistic revision комнаты: FORCE_INCREMENT делает child-only команды
-- видимыми конкурентной REPEATABLE_READ транзакции и защищает от устаревшего состава/ответов.
-- Не является revision пользовательских настроек; legacy-строки получают безопасный default.
alter table quiz_rooms add column command_revision bigint not null default 0;
alter table quiz_rooms add constraint ck_quiz_room_command_revision check (command_revision >= 0);
