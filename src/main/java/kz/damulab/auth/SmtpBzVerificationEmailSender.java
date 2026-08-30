package kz.damulab.auth;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

import kz.damulab.users.AppUser;

/**
 * HTTP-клиент отправки транзакционных писем через {@code POST /v1/smtp/send} SMTP.BZ.
 *
 * <p>Ключ передаётся в заголовке {@code Authorization} без префикса Bearer, как требует API
 * провайдера. В логах нет ни ключа, ни одноразового токена.</p>
 */
@Component
public class SmtpBzVerificationEmailSender implements VerificationEmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpBzVerificationEmailSender.class);
    private static final String SEND_ENDPOINT = "/smtp/send";

    private final EmailVerificationProperties properties;
    private final RestClient.Builder restClientBuilder;

    public SmtpBzVerificationEmailSender(
            EmailVerificationProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public EmailSendResult sendVerificationEmail(AppUser user, String rawToken) {
        requireConfigured(properties.getApiBaseUrl(), "EMAIL_API_BASE_URL");
        requireConfigured(properties.getApiKey(), "EMAIL_API_KEY");
        requireConfigured(properties.getFrom(), "EMAIL_FROM");
        requireConfigured(properties.getPublicBaseUrl(), "APP_PUBLIC_BASE_URL");

        String verificationUrl = UriComponentsBuilder
                .fromUriString(stripTrailingSlash(properties.getPublicBaseUrl()))
                .path("/activate-account")
                .queryParam("token", rawToken)
                .build()
                .encode()
                .toUriString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", properties.getSenderName());
        body.put("from", properties.getFrom());
        body.put("to", user.getEmail());
        body.put("subject", "Damulab аккаунтын белсендіру / Активация аккаунта Damulab");
        body.put("html", html(user.getFullName(), verificationUrl));

        try {
            ResponseEntity<SmtpBzSendResponse> response = restClientBuilder
                    .baseUrl(stripTrailingSlash(properties.getApiBaseUrl()))
                    .defaultHeader("Authorization", properties.getApiKey())
                    .build()
                    .post()
                    .uri(SEND_ENDPOINT)
                    .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                    .body(body)
                    .retrieve()
                    .toEntity(SmtpBzSendResponse.class);
            SmtpBzSendResponse payload = response.getBody();
            if (payload == null
                    || payload.to() == null
                    || !payload.to().equalsIgnoreCase(user.getEmail())
                    || (payload.result() && (payload.messageid() == null || payload.messageid().isBlank()))) {
                throw deliveryError(
                        EmailDeliveryAttemptStatus.INVALID_RESPONSE,
                        response.getStatusCode().value(),
                        "INVALID_PROVIDER_RESPONSE",
                        "SMTP.BZ вернул неполный ответ",
                        null
                );
            }
            log.info(
                    "SMTP.BZ завершил отправку письма подтверждения: userId={}, result={}, messageId={}",
                    user.getId(), payload.result(), payload.messageid()
            );
            return new EmailSendResult(
                    payload.result(),
                    payload.to(),
                    payload.messageid(),
                    response.getStatusCode().value()
            );
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("SMTP.BZ вернул HTTP-ошибку для userId={}, status={}", user.getId(), ex.getStatusCode().value());
            throw deliveryError(
                    EmailDeliveryAttemptStatus.HTTP_ERROR,
                    ex.getStatusCode().value(),
                    "PROVIDER_HTTP_ERROR",
                    "SMTP.BZ вернул HTTP " + ex.getStatusCode().value(),
                    ex
            );
        } catch (ResourceAccessException ex) {
            log.warn("SMTP.BZ недоступен для userId={}", user.getId());
            throw deliveryError(
                    EmailDeliveryAttemptStatus.NETWORK_ERROR,
                    null,
                    "PROVIDER_UNAVAILABLE",
                    "SMTP.BZ недоступен",
                    ex
            );
        } catch (RestClientException ex) {
            log.warn("Ошибка клиента SMTP.BZ для userId={}", user.getId());
            throw deliveryError(
                    EmailDeliveryAttemptStatus.INVALID_RESPONSE,
                    null,
                    "PROVIDER_CLIENT_ERROR",
                    "Не удалось обработать ответ SMTP.BZ",
                    ex
            );
        }
    }

    private String html(String fullName, String verificationUrl) {
        String safeName = HtmlUtils.htmlEscape(fullName);
        String safeUrl = HtmlUtils.htmlEscape(verificationUrl);
        return """
                <!doctype html>
                <html lang="kk">
                <head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;color:#172b4d;line-height:1.5">
                  <h2>Damulab аккаунтын белсендіру</h2>
                  <p>Сәлеметсіз бе, %s!</p>
                  <p>Аккаунтыңызды белсендіру үшін төмендегі батырманы басыңыз:</p>
                  <p><a href="%s" rel="noreferrer" style="display:inline-block;padding:12px 20px;background:#1668dc;color:#fff;text-decoration:none;border-radius:8px">Аккаунтты белсендіру</a></p>
                  <p>Сілтеме %s жарамды. Егер аккаунтты сіз жасамаған болсаңыз, бұл хатты елемеңіз.</p>
                  <hr style="border:0;border-top:1px solid #e4e7ec;margin:24px 0">
                  <h2>Активация аккаунта Damulab</h2>
                  <p>Здравствуйте, %s!</p>
                  <p>Чтобы активировать аккаунт, нажмите кнопку:</p>
                  <p><a href="%s" rel="noreferrer" style="display:inline-block;padding:12px 20px;background:#1668dc;color:#fff;text-decoration:none;border-radius:8px">Активировать аккаунт</a></p>
                  <p>Ссылка действует %s. Если аккаунт создавали не вы, просто проигнорируйте письмо.</p>
                  <p style="font-size:12px;color:#667085">Если кнопка не работает: <a href="%s">%s</a></p>
                </body>
                </html>
                """.formatted(
                safeName, safeUrl, humanTtlKazakh(),
                safeName, safeUrl, humanTtlRussian(),
                safeUrl, safeUrl
        );
    }

    private String humanTtlRussian() {
        long hours = properties.getTokenTtl().toHours();
        return hours == 24 ? "24 часа" : hours + " ч.";
    }

    private String humanTtlKazakh() {
        long hours = properties.getTokenTtl().toHours();
        return hours == 24 ? "24 сағат бойы" : hours + " сағат бойы";
    }

    private void requireConfigured(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw deliveryError(
                    EmailDeliveryAttemptStatus.CONFIGURATION_ERROR,
                    null,
                    "MISSING_CONFIGURATION",
                    "Не задана переменная " + envName,
                    null
            );
        }
    }

    private EmailDeliveryException deliveryError(
            EmailDeliveryAttemptStatus status,
            Integer httpStatus,
            String errorCode,
            String message,
            Throwable cause
    ) {
        return new EmailDeliveryException(status, httpStatus, errorCode, message, cause);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
