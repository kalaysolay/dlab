-- Soft-delete тем: deleted_at вместо hard-delete.
-- Списки/пикеры смотрят только deleted_at is null.
--
-- Старый unique(subject, grade, parent, code) снимаем: иначе soft-deleted строка
-- блокирует повторное создание того же code. Уникальность среди АКТИВНЫХ тем
-- проверяет ContentGraphService.existsDuplicate (как и раньше для root с parent_id null).
-- Partial unique index (WHERE deleted_at is null) не используем — H2 в тестах его не понимает.

alter table topics
    add column deleted_at timestamp with time zone;

alter table topics
    drop constraint if exists topics_subject_id_grade_id_parent_id_code_key;

create index idx_topics_scope_code
    on topics (subject_id, grade_id, parent_id, code);

create index idx_topics_deleted_at
    on topics (deleted_at);
