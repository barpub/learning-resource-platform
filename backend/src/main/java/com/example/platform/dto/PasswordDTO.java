package com.example.platform.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

public class PasswordDTO {
    @NotBlank
    private String oldPassword;
    @NotBlank
    @Size(min = 6, max = 50)
    private String newPassword;

    public String getOldPassword() {
        return oldPassword;
    }

    public void setOldPassword(String oldPassword) {
        this.oldPassword = oldPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}
