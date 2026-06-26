-- Добавляем поле для хранения полного JSON браузерной Web Push подписки.
-- Содержит: endpoint, keys.p256dh, keys.auth — всё необходимое для отправки через RFC 8030.
-- Nullable, так как старые записи (StubPushProvider тесты) не имеют этого поля.
alter table device_tokens add column subscription_json text;
