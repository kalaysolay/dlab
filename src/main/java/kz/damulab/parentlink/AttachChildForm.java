package kz.damulab.parentlink;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class AttachChildForm {

    @NotBlank
    @Pattern(regexp = "[A-Z0-9]{6,12}")
    private String code;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code == null ? null : code.trim().toUpperCase();
    }
}
