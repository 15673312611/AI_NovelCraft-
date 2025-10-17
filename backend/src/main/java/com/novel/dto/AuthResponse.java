package com.novel.dto;

import com.novel.domain.entity.User;

public class AuthResponse {
    private User user;
    private String token;
    private String tokenType = "Bearer";

    public AuthResponse() {}
    public AuthResponse(User user, String token) { this.user = user; this.token = token; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
}

