package com.novel.dto;

import com.novel.domain.entity.User;

public class AuthResponse {
    private SafeUserDto user;
    private String token;
    private String tokenType = "Bearer";

    public AuthResponse() {}
    public AuthResponse(User user, String token) { this.user = SafeUserDto.from(user); this.token = token; }
    public AuthResponse(SafeUserDto user, String token) { this.user = user; this.token = token; }
    public SafeUserDto getUser() { return user; }
    public void setUser(SafeUserDto user) { this.user = user; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }
}

