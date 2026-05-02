package kz.damulab.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ParentProfileForm {

    @NotBlank
    @Size(max = 255)
    private String fullName;

    @Size(max = 64)
    private String phone;

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
}
