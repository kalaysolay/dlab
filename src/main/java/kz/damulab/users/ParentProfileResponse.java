package kz.damulab.users;

public record ParentProfileResponse(
        Long userId,
        String email,
        String fullName,
        String phone
) {
}
