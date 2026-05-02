package kz.damulab.auth;

import java.util.Set;

public record CurrentUserResponse(
        Long id,
        String email,
        String fullName,
        Set<String> roles
) {
}
