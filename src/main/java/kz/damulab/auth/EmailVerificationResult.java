package kz.damulab.auth;

/** Итог перехода по одноразовой ссылке активации. */
public enum EmailVerificationResult {
    VERIFIED,
    EXPIRED,
    INVALID
}
