package kz.damulab.users;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StudentProfileForm {

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Size(max = 64)
    private String phone;

    @Min(1)
    @Max(5)
    private Integer gradeNo;

    @Pattern(regexp = "ru|kk")
    private String preferredLanguage = "ru";

    private Boolean lessonRemindersEnabled;

    private Boolean weeklyParentReportEnabled;

    private Boolean sessionResultPushEnabled;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public Integer getGradeNo() {
        return gradeNo;
    }

    public void setGradeNo(Integer gradeNo) {
        this.gradeNo = gradeNo;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public Boolean getLessonRemindersEnabled() {
        return lessonRemindersEnabled;
    }

    public void setLessonRemindersEnabled(Boolean lessonRemindersEnabled) {
        this.lessonRemindersEnabled = lessonRemindersEnabled;
    }

    public Boolean getWeeklyParentReportEnabled() {
        return weeklyParentReportEnabled;
    }

    public void setWeeklyParentReportEnabled(Boolean weeklyParentReportEnabled) {
        this.weeklyParentReportEnabled = weeklyParentReportEnabled;
    }

    public Boolean getSessionResultPushEnabled() {
        return sessionResultPushEnabled;
    }

    public void setSessionResultPushEnabled(Boolean sessionResultPushEnabled) {
        this.sessionResultPushEnabled = sessionResultPushEnabled;
    }
}
