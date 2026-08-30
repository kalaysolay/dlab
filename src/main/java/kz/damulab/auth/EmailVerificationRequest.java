package kz.damulab.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Запрос повторной отправки письма подтверждения. */
public record EmailVerificationRequest(
        @NotBlank @Email @Size(max = 320) String email
) {
}
