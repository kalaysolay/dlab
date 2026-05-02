package kz.damulab.parentlink;

public record ChildResponse(
        Long studentId,
        String email,
        String fullName,
        Integer gradeNo,
        String preferredLanguage
) {
}
