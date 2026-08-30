package kz.damulab.auth;

/** Нормализованный итог отдельного обращения к почтовому провайдеру. */
public enum EmailDeliveryAttemptStatus {
    ACCEPTED,
    REJECTED,
    HTTP_ERROR,
    NETWORK_ERROR,
    CONFIGURATION_ERROR,
    INVALID_RESPONSE
}
