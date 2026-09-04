package kz.damulab.parentlink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ConfirmParentLinkInvitationRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token
) {
}
