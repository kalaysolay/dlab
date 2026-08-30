package kz.damulab.auth;

/** Нормализованный успешный HTTP-ответ SMTP.BZ без хранения исходного JSON. */
public record EmailSendResult(
        boolean result,
        String recipientEmail,
        String providerMessageId,
        int httpStatus
) {
}
