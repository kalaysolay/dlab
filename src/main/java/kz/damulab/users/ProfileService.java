package kz.damulab.users;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final StudentProfileRepository studentProfiles;
    private final ParentProfileRepository parentProfiles;

    public ProfileService(StudentProfileRepository studentProfiles, ParentProfileRepository parentProfiles) {
        this.studentProfiles = studentProfiles;
        this.parentProfiles = parentProfiles;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getStudentProfile(String email) {
        return toStudentResponse(findStudent(email));
    }

    @Transactional
    public StudentProfileResponse updateStudentProfile(String email, StudentProfileForm form) {
        StudentProfile profile = findStudent(email);
        profile.getUser().updateProfile(form.getFullName(), form.getPhone());
        profile.update(form.getGradeNo(), form.getPreferredLanguage());
        profile.updateNotificationSettings(
                valueOrExisting(form.getLessonRemindersEnabled(), profile.isLessonRemindersEnabled()),
                valueOrExisting(form.getWeeklyParentReportEnabled(), profile.isWeeklyParentReportEnabled()),
                valueOrExisting(form.getSessionResultPushEnabled(), profile.isSessionResultPushEnabled())
        );
        return toStudentResponse(profile);
    }

    @Transactional
    public StudentProfileResponse updateStudentLanguage(String email, String preferredLanguage) {
        StudentProfile profile = findStudent(email);
        profile.update(profile.getGradeNo(), preferredLanguage);
        return toStudentResponse(profile);
    }

    @Transactional(readOnly = true)
    public ParentProfileResponse getParentProfile(String email) {
        return toParentResponse(findParent(email));
    }

    @Transactional
    public ParentProfileResponse updateParentProfile(String email, ParentProfileForm form) {
        ParentProfile profile = findParent(email);
        profile.getUser().updateProfile(form.getFullName(), form.getPhone());
        profile.updatePhone(form.getPhone());
        return toParentResponse(profile);
    }

    private StudentProfile findStudent(String email) {
        return studentProfiles.findByUserEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Student profile not found: " + email));
    }

    private ParentProfile findParent(String email) {
        return parentProfiles.findByUserEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Parent profile not found: " + email));
    }

    private StudentProfileResponse toStudentResponse(StudentProfile profile) {
        AppUser user = profile.getUser();
        return new StudentProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                profile.getGradeNo(),
                profile.getPreferredLanguage(),
                profile.isLessonRemindersEnabled(),
                profile.isWeeklyParentReportEnabled(),
                profile.isSessionResultPushEnabled()
        );
    }

    private boolean valueOrExisting(Boolean value, boolean existing) {
        return value == null ? existing : value;
    }

    private ParentProfileResponse toParentResponse(ParentProfile profile) {
        AppUser user = profile.getUser();
        return new ParentProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone()
        );
    }
}
