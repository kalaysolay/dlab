-- Изолированные денежные резервы: ни цитат, ни профиля обучения. Работают независимо от удаления истории.
create table speaking_budget_accounts (
    scope_key varchar(64) primary key,
    student_profile_id bigint references student_profiles(id) on delete cascade,
    usage_day date not null,
    reserved_micros bigint not null default 0 check(reserved_micros >= 0),
    llm_calls bigint not null default 0,
    stt_calls bigint not null default 0,
    speech_calls bigint not null default 0
);
insert into speaking_budget_accounts(scope_key,usage_day) values ('module','2000-01-01');

-- Не подставляем текущую модель старым ответам: их происхождение неизвестно и остаётся null.
alter table speaking_turns add column provider_code varchar(16);
alter table speaking_turns add column model_name varchar(200);
alter table speaking_turns add column prompt_version varchar(64);
alter table speaking_generations add column provider_code varchar(16);
alter table speaking_generations add column model_name varchar(200);
alter table speaking_generations add column prompt_version varchar(64);
