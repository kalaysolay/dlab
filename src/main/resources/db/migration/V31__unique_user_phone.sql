-- Телефон принадлежит учётной записи. Историческое поле parent_profiles.phone дублировало
-- app_users.phone и могло расходиться с ним, поэтому сначала переносим недостающее значение.
update app_users u
set phone = (
    select p.phone
    from parent_profiles p
    where p.user_id = u.id
)
where u.phone is null
  and exists (
      select 1
      from parent_profiles p
      where p.user_id = u.id and p.phone is not null
  );

update app_users
set phone = null
where phone is not null and trim(phone) = '';

-- Приводим распространённые варианты записи к одному E.164-представлению. Если после
-- нормализации обнаружатся дубли, создание ограничения намеренно остановит миграцию:
-- выбирать владельца общего номера автоматически небезопасно.
update app_users
set phone = replace(replace(replace(replace(replace(trim(phone), ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')
where phone is not null;

update app_users
set phone = concat('+7', substring(phone, 2))
where length(phone) = 11 and substring(phone, 1, 1) = '8';

update app_users
set phone = concat('+', phone)
where length(phone) = 11 and substring(phone, 1, 1) = '7';

update app_users
set phone = concat('+7', phone)
where length(phone) = 10 and substring(phone, 1, 1) <> '+';

alter table app_users
    add constraint uq_app_users_phone unique (phone);

alter table parent_profiles
    drop column phone;
