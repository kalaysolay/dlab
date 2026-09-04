-- Добавляем локализованные описания и ссылку на PNG-иконку предмета.
alter table subjects add column description_ru varchar(2000);
alter table subjects add column description_kk varchar(2000);
alter table subjects add column icon_storage_key varchar(64);

-- Для существующих предметов используем название как безопасное начальное описание.
update subjects
set description_ru = title_ru,
    description_kk = title_kk;

alter table subjects alter column description_ru set not null;
alter table subjects alter column description_kk set not null;
