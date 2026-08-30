package kz.damulab.auth;

/** Ошибка конфигурации или HTTP-вызова почтового провайдера. */
public class EmailDeliveryException extends RuntimeException {

    private final EmailDeliveryAttemptStatus status;
    private final Integer httpStatus;
    private final String errorCode;

    public EmailDeliveryException(
            EmailDeliveryAttemptStatus status,
            Integer httpStatus,
            String errorCode,
            String message
    ) {
        this(status, httpStatus, errorCode, message, null);
    }

    public EmailDeliveryException(
            EmailDeliveryAttemptStatus status,
            Integer httpStatus,
            String errorCode,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.status = status;
        this.httpStatus = httpStatus;
        this.errorCode = errorCode;
    }

    public EmailDeliveryAttemptStatus getStatus() {
        return status;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
