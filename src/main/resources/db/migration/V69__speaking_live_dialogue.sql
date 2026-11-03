-- Отдельный маршрут не меняет провайдеры вопросов, лекций или переводчика.
alter table ai_runtime_settings drop constraint chk_ai_runtime_settings_usage;
alter table ai_runtime_settings add constraint chk_ai_runtime_settings_usage
    check (usage_type in ('QUESTIONS','LECTURES','TRANSLATIONS','SPEAKING'));
insert into ai_runtime_settings(usage_type,provider,model_name,quality_threshold,updated_by)
    select 'SPEAKING',provider,model_name,quality_threshold,'migration' from ai_runtime_settings where usage_type='QUESTIONS';

alter table speaking_sessions add column total_turns integer not null default 0;
update speaking_sessions set total_turns=accepted_turns;
alter table speaking_sessions add column off_topic_count integer not null default 0;
alter table speaking_turns add column disposition varchar(16) not null default 'NORMAL';
alter table speaking_turns add column request_hash varchar(64);
alter table speaking_daily_usage add column llm_calls integer not null default 0;
alter table speaking_daily_usage add column speech_calls integer not null default 0;

-- Lease не содержит исходную реплику. Неподходящий вход не сохраняется даже ради повтора запроса.
create table speaking_generations (
    id uuid primary key,
    student_profile_id bigint not null,
    session_id uuid not null,
    request_id uuid not null,
    request_hash varchar(64) not null,
    session_version bigint not null,
    state varchar(16) not null,
    active_slot integer,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    foreign key(session_id,student_profile_id) references speaking_sessions(id,student_profile_id) on delete cascade,
    unique(session_id,request_id),
    unique(student_profile_id,active_slot),
    check ((state='PROCESSING' and active_slot=1) or (state<>'PROCESSING' and active_slot is null))
);
create index speaking_generations_expiry on speaking_generations(expires_at);
