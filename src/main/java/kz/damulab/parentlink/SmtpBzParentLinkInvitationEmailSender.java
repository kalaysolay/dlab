package kz.damulab.parentlink;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;
import kz.damulab.auth.EmailDeliveryAttemptStatus;
import kz.damulab.auth.EmailDeliveryException;
import kz.damulab.auth.EmailSendResult;
import kz.damulab.auth.EmailVerificationProperties;
import kz.damulab.auth.SmtpBzSendResponse;

/**
 * SMTP.BZ-адаптер приглашений. В отличие от профиля, письмо намеренно не содержит имён
 * ребёнка или родителя: внешний провайдер видит только адрес доставки и одноразовый URL.
 */
@Component
public class SmtpBzParentLinkInvitationEmailSender implements ParentLinkInvitationEmailSender {

    private final EmailVerificationProperties mailProperties;
    private final RestClient.Builder restClientBuilder;

    public SmtpBzParentLinkInvitationEmailSender(
            EmailVerificationProperties mailProperties,
            RestClient.Builder restClientBuilder
    ) {
        this.mailProperties = mailProperties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public EmailSendResult sendInvitation(String studentEmail, String rawToken) {
        requireConfigured(mailProperties.getApiBaseUrl(), "EMAIL_API_BASE_URL");
        requireConfigured(mailProperties.getApiKey(), "EMAIL_API_KEY");
        requireConfigured(mailProperties.getFrom(), "EMAIL_FROM");
        requireConfigured(mailProperties.getPublicBaseUrl(), "APP_PUBLIC_BASE_URL");
        String url = UriComponentsBuilder
                .fromUriString(stripTrailingSlash(mailProperties.getPublicBaseUrl()))
                .path("/parent-link-invitations/confirm")
                .queryParam("token", rawToken)
                .build().encode().toUriString();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", mailProperties.getSenderName());
        body.put("from", mailProperties.getFrom());
        body.put("to", studentEmail);
        body.put("subject", "Damulab: ата-анамен байланысты растау / Подтверждение связи с родителем");
        body.put("html", html(url));

        try {
            ResponseEntity<SmtpBzSendResponse> response = restClientBuilder
                    .baseUrl(stripTrailingSlash(mailProperties.getApiBaseUrl()))
                    .defaultHeader("Authorization", mailProperties.getApiKey())
                    .build().post().uri("/smtp/send")
                    .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                    .body(body).retrieve().toEntity(SmtpBzSendResponse.class);
            SmtpBzSendResponse payload = response.getBody();
            if (payload == null
                    || payload.to() == null
                    || !payload.to().equalsIgnoreCase(studentEmail)
                    || (payload.result() && (payload.messageid() == null || payload.messageid().isBlank()))) {
                throw error(EmailDeliveryAttemptStatus.INVALID_RESPONSE, null, "INVALID_PROVIDER_RESPONSE", null);
            }
            return new EmailSendResult(payload.result(), payload.to(), payload.messageid(), response.getStatusCode().value());
        } catch (EmailDeliveryException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw error(EmailDeliveryAttemptStatus.HTTP_ERROR, ex.getStatusCode().value(), "PROVIDER_HTTP_ERROR", ex);
        } catch (ResourceAccessException ex) {
            throw error(EmailDeliveryAttemptStatus.NETWORK_ERROR, null, "PROVIDER_UNAVAILABLE", ex);
        } catch (RestClientException ex) {
            throw error(EmailDeliveryAttemptStatus.INVALID_RESPONSE, null, "PROVIDER_CLIENT_ERROR", ex);
        }
    }

    private String html(String url) {
        String safeUrl = HtmlUtils.htmlEscape(url);
        return """
                <!doctype html><html lang="kk"><head><meta charset="UTF-8"></head>
                <body style="font-family:Arial,sans-serif;color:#172b4d;line-height:1.5">
                <h2>Ата-анамен байланысты растау</h2>
                <p>Damulab жүйесінде ата-ана профилімен байланыс сұрауы жасалды.</p>
                <p><a href="%s" rel="noreferrer" style="display:inline-block;padding:12px 20px;background:#1668dc;color:#fff;text-decoration:none;border-radius:8px">Сұрауды тексеру</a></p>
                <hr style="border:0;border-top:1px solid #e4e7ec;margin:24px 0">
                <h2>Подтверждение связи с родителем</h2>
                <p>В Damulab создан запрос на привязку вашего аккаунта к профилю родителя.</p>
                <p><a href="%s" rel="noreferrer" style="display:inline-block;padding:12px 20px;background:#1668dc;color:#fff;text-decoration:none;border-radius:8px">Проверить запрос</a></p>
                <p>Если вы не ожидали это письмо, ничего подтверждать не нужно.</p>
                </body></html>
                """.formatted(safeUrl, safeUrl);
    }

    private void requireConfigured(String value, String envName) {
        if (value == null || value.isBlank()) {
            throw error(EmailDeliveryAttemptStatus.CONFIGURATION_ERROR, null, "MISSING_" + envName, null);
        }
    }

    private EmailDeliveryException error(
            EmailDeliveryAttemptStatus status, Integer httpStatus, String code, Throwable cause
    ) {
        return new EmailDeliveryException(status, httpStatus, code, "Не удалось отправить приглашение", cause);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
