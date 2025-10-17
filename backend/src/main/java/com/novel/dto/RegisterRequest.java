package com.novel.dto;

import javax.validation.constraints.*;

public class RegisterRequest {
    @NotBlank @Size(min = 3, max = 20) @Pattern(regexp = "^[a-zA-Z0-9_]+$")
    private String username;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 6, max = 20)
    private String password;

    public RegisterRequest() {}
    public RegisterRequest(String username, String email, String password) { this.username = username; this.email = email; this.password = password; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}

