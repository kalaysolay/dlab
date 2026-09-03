package kz.damulab.parentlink;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Ввод родителя; ответ на него намеренно не содержит сведений о найденном аккаунте. */
public class InviteChildByEmailForm {

    @NotBlank
    @Email
    @Size(max = 320)
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
