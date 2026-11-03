-- Промежуточная расшифровка отделена от принятого ответа и учебного наблюдения.
-- Исходное аудио в БД не сохраняется. При подтверждении исходный текст очищается.
create table speaking_voice_attempts (
    id uuid primary key,
    student_profile_id bigint not null,
    session_id uuid not null,
    request_id uuid not null,
    session_version bigint not null,
    audio_hash varchar(64) not null,
    state varchar(16) not null,
    active_slot integer,
    transcript varchar(2000),
    audio_seconds integer not null default 0,
    usage_day date not null,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    foreign key(session_id, student_profile_id) references speaking_sessions(id, student_profile_id) on delete cascade,
    unique(session_id, request_id),
    unique(student_profile_id, active_slot),
    check ((state='PROCESSING' and active_slot=1) or (state<>'PROCESSING' and active_slot is null))
);
create index speaking_voice_expiry on speaking_voice_attempts(expires_at);
alter table speaking_turns add column voice_attempt_id uuid;
alter table speaking_turns add column audio_seconds integer not null default 0;
alter table speaking_daily_usage add column audio_seconds integer not null default 0;
alter table speaking_daily_usage add column voice_window timestamp with time zone;
alter table speaking_daily_usage add column voice_requests integer not null default 0;
