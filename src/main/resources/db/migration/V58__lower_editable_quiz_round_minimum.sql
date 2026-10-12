-- Готовые словарные игры показывают ученику редактируемый контрол времени.
-- После перехода общего интерфейса на 5 секунд старые опубликованные версии
-- продолжали отдавать браузеру минимум 10 секунд.
update quiz_game_versions
set min_round_seconds = 5
where round_seconds_editable = true
  and min_round_seconds > 5
  and game_id in (
      select id
      from quiz_games
      where code in (
          'basic-ru-en-300-v1',
          'basic-en-ru-400-v1',
          'basic-ru-kk-300-v1'
      )
  );
