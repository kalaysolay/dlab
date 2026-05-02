package kz.damulab.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kz.damulab.users.RoleCode;

public class RegisterForm {

    @NotBlank
    @Email
    @Size(max = 320)
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Size(max = 64)
    private String phone;

    @NotNull
    private RoleCode role = RoleCode.STUDENT;

    @Min(1)
    @Max(5)
    private Integer gradeNo;

    @Size(max = 8)
    private String preferredLanguage = "ru";

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

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
