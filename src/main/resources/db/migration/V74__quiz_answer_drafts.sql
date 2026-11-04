-- Текущий выбор хранится отдельно от окончательного ответа и переживает закрытие вкладки.
alter table quiz_participants add column draft_round_id bigint references quiz_rounds(id) on delete set null;
alter table quiz_participants add column draft_answer_json text;
