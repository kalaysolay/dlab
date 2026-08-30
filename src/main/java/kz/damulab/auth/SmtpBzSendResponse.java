package kz.damulab.auth;

/** Поля ответа {@code POST /v1/smtp/send}, которые нужны журналу доставки. */
public record SmtpBzSendResponse(boolean result, String to, String messageid) {
}
