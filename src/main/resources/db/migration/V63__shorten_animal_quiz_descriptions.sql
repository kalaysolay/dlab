-- Убираем подсказку из уже загруженных описаний двух игр; авторский текст сохраняется.
-- Снимки завершённых комнат и содержание объяснений не затрагиваются.
update quiz_game_versions
set description_ru=replace(description_ru, ' Короткое объяснение и источник — в разборе после игры.', ''),
    description_kk=replace(description_kk, ' Қысқаша түсіндірме мен дереккөз ойыннан кейінгі талдауда беріледі.', ''),
    updated_at=current_timestamp
where game_id in (select id from quiz_games where code in ('animal-wonders-v1', 'ocean-animals-v1'))
  and (description_ru like '% Короткое объяснение и источник — в разборе после игры.%'
       or description_kk like '% Қысқаша түсіндірме мен дереккөз ойыннан кейінгі талдауда беріледі.%');
