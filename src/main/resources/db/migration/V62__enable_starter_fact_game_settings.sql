-- Ранее загруженные фактовые игры имели фиксированные 5 вопросов по 20 секунд.
-- Разрешаем ведущему выбирать параметры; текущие значения и снимки комнат сохраняются.
update quiz_game_versions
set question_count_editable=true, round_seconds_editable=true,
    min_question_count=1, min_round_seconds=5, max_round_seconds=120,
    max_question_count=greatest(default_question_count, (
        select count(*) from quiz_fact_game_configs c
        join fact_statements f on f.set_id=c.fact_set_id and f.status='PUBLISHED'
        join fact_statement_versions v on v.id=f.published_version_id and v.status='PUBLISHED'
        where c.game_version_id=quiz_game_versions.id
    )), updated_at=current_timestamp
where game_type='TRUE_FALSE' and game_id in (
    select id from quiz_games
    where code in ('starter-space-facts-v1','animal-wonders-v1','ocean-animals-v1')
);
