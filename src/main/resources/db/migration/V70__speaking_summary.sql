-- Старые сессии остаются с null: отсутствие счётчика не означает отсутствие помощи.
alter table speaking_sessions add column help_uses integer;
alter table speaking_sessions add constraint speaking_help_uses_nonnegative check (help_uses is null or help_uses >= 0);
