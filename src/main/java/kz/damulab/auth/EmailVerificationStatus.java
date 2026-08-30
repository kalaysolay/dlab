package kz.damulab.auth;

/** Состояние единственной актуальной активации пользователя. */
public enum EmailVerificationStatus {
    PENDING,
    VERIFIED,
    EXPIRED
}
