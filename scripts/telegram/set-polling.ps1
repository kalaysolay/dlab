# Явное переключение одного бота на polling, без удаления накопленных сообщений.
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:TELEGRAM_BOT_TOKEN)) { throw 'Не задан TELEGRAM_BOT_TOKEN' }
try {
    $result = Invoke-RestMethod -Method Post -Uri ('https://api.telegram.org/bot' + $env:TELEGRAM_BOT_TOKEN + '/deleteWebhook') -ContentType 'application/json' -Body '{"drop_pending_updates":false}'
    if (-not $result.ok) { throw 'Telegram отклонил переключение' }
    Write-Output 'Webhook удалён. Запустите один экземпляр приложения с TELEGRAM_MODE=polling.'
} catch { throw 'Не удалось переключить бота в polling. Проверьте токен и соединение.' }
