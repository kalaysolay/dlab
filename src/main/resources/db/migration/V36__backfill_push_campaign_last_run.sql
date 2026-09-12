-- Раньше PushCampaignRunner передавал в сервис detached entity, поэтому история
-- запуска сохранялась в push_campaign_runs, а push_campaigns.last_run_at оставался null.
-- Восстанавливаем метку по последнему фактическому запуску, чтобы админка не показывала
-- «ещё не запускалась» для уже выполненных кампаний и защита от повторного запуска
-- учитывала существующую историю сразу после обновления приложения.
update push_campaigns
set last_run_at = (
    select max(r.triggered_at)
    from push_campaign_runs r
    where r.campaign_id = push_campaigns.id
)
where last_run_at is null
  and exists (
      select 1
      from push_campaign_runs r
      where r.campaign_id = push_campaigns.id
  );
