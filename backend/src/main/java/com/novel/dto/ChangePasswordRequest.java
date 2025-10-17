package com.novel.dto;

import javax.validation.constraints.*;

public class ChangePasswordRequest {
    @NotBlank private String oldPassword;
    @NotBlank @Size(min = 6, max = 20) private String newPassword;
    public ChangePasswordRequest() {}
    public ChangePasswordRequest(String oldPassword, String newPassword) { this.oldPassword = oldPassword; this.newPassword = newPassword; }
    public String getOldPassword() { return oldPassword; }
    public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}

