package kz.damulab.users;

public record StudentProfileResponse(
        Long userId,
        String email,
        String fullName,
        String phone,
        Integer gradeNo,
        String preferredLanguage,
        boolean lessonRemindersEnabled,
        boolean weeklyParentReportEnabled,
        boolean sessionResultPushEnabled
) {
}
