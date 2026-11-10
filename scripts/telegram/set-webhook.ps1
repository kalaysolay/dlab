# Регистрирует webhook только по явному запуску оператором; секреты берутся из окружения.
$ErrorActionPreference = 'Stop'
foreach ($name in @('TELEGRAM_BOT_TOKEN', 'TELEGRAM_WEBHOOK_SECRET', 'APP_PUBLIC_BASE_URL')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "Не задана переменная $name"
    }
}
$publicUri = [Uri]$env:APP_PUBLIC_BASE_URL
if ($publicUri.Scheme -ne 'https') { throw 'Для webhook нужен публичный HTTPS-адрес' }
$payload = @{
    url = $env:APP_PUBLIC_BASE_URL.TrimEnd('/') + '/api/telegram/webhook'
    secret_token = $env:TELEGRAM_WEBHOOK_SECRET
    allowed_updates = @('message')
} | ConvertTo-Json
try {
    $result = Invoke-RestMethod -Method Post -Uri ('https://api.telegram.org/bot' + $env:TELEGRAM_BOT_TOKEN + '/setWebhook') -ContentType 'application/json' -Body $payload
    if (-not $result.ok) { throw 'Telegram отклонил webhook' }
    Write-Output 'Webhook Telegram зарегистрирован.'
} catch {
    # Стандартное исключение PowerShell может содержать URI с токеном бота.
    throw 'Не удалось зарегистрировать webhook. Проверьте настройки и доступность Telegram.'
}
