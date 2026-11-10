package kz.damulab.parentreports;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Прямой Bot API без дополнительных SDK; ошибки не содержат URL с секретом бота. */
@Component
public class TelegramClient {
    private final TelegramProperties config;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    public TelegramClient(TelegramProperties config, ObjectMapper mapper) { this.config = config; this.mapper = mapper; }

    /** Сервер Telegram держит запрос до 20 секунд; отдельный поток не мешает отправке отчётов. */
    public JsonNode updates(long offset) {
        if (!config.available() || !config.polling()) throw new Failure("disabled", 0, false, false);
        try {
            var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + config.botToken() + "/getUpdates"))
                    .timeout(Duration.ofSeconds(30)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "offset", offset, "timeout", 20, "limit", 50, "allowed_updates", java.util.List.of("message"))))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            var json = mapper.readTree(response.body());
            if (!json.path("ok").asBoolean()) throw new Failure("telegram_" + json.path("error_code").asInt(response.statusCode()),
                    Math.max(5, json.path("parameters").path("retry_after").asInt(5)), false, true);
            if (!json.path("result").isArray()) throw new Failure("invalid_response", 5, false, true);
            return json.path("result");
        } catch (Failure ex) { throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new Failure("poll_interrupted", 5, false, true);
        } catch (Exception ex) { throw new Failure("poll_network_error", 5, false, true); }
    }

    /** Telegram принял сообщение; это не подтверждение прочтения. */
    public long send(long chatId, String text, String reportUrl) {
        if (!config.available()) throw new Failure("disabled", 0, false, false);
        try {
            var body = new java.util.HashMap<String, Object>();
            body.put("chat_id", chatId); body.put("text", text);
            body.put("link_preview_options", Map.of("is_disabled", true));
            // Локальный localhost недоступен на телефоне родителя: кнопку добавляем после HTTPS-деплоя.
            if (reportUrl != null && reportUrl.startsWith("https://")) body.put("reply_markup", Map.of("inline_keyboard", java.util.List.of(
                    java.util.List.of(Map.of("text", "Толық есеп / Полный отчёт", "url", reportUrl)))));
            var request = HttpRequest.newBuilder(URI.create("https://api.telegram.org/bot" + config.botToken() + "/sendMessage"))
                    .timeout(Duration.ofSeconds(12)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = mapper.readTree(response.body());
            if (!json.path("ok").asBoolean()) {
                int code = json.path("error_code").asInt(response.statusCode());
                throw new Failure("telegram_" + code, Math.max(1, json.path("parameters").path("retry_after").asInt(60)),
                        code == 403, code == 429 || code >= 500);
            }
            return json.path("result").path("message_id").asLong();
        } catch (Failure ex) { throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw new Failure("unknown_outcome", 0, false, false);
        } catch (Exception ex) {
            // После сетевого сбоя нельзя узнать, принято ли сообщение. Не повторяем автоматически,
            // чтобы не прислать родителю два одинаковых отчёта.
            throw new Failure("unknown_outcome", 0, false, false);
        }
    }
    public static class Failure extends RuntimeException {
        final String code; final int retrySeconds; final boolean blocked; final boolean retry;
        Failure(String code, int seconds, boolean blocked, boolean retry) {
            super(code); this.code = code; this.retrySeconds = seconds; this.blocked = blocked; this.retry = retry;
        }
    }
}
