package kz.damulab.auth;

import kz.damulab.users.AppUser;

/** Результат регистрации с отдельно зафиксированным итогом первой отправки письма. */
public record RegistrationResult(
        AppUser user,
        boolean verificationRequired,
        EmailDeliveryAttemptStatus deliveryStatus
) {
    public boolean emailAccepted() {
        return deliveryStatus == EmailDeliveryAttemptStatus.ACCEPTED;
    }
}
