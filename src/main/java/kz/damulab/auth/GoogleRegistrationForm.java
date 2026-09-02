package kz.damulab.auth;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.damulab.users.RoleCode;

/** Данные профиля, которых нет в Google и которые новый пользователь выбирает один раз. */
public class GoogleRegistrationForm {

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @NotNull
    private RoleCode role = RoleCode.STUDENT;

    @Min(1)
    @Max(5)
    private Integer gradeNo;

    @Size(max = 8)
    private String preferredLanguage = "ru";

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public RoleCode getRole() {
        return role;
    }

    public void setRole(RoleCode role) {
        this.role = role;
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
}
