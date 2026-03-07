package com.novel.dto;

/**
 * Wechat Login Request
 */
public class WechatLoginRequest {
    
    /**
     * Wechat auth code
     */
    private String code;
    
    /**
     * State for CSRF protection
     */
    private String state;
    
    /**
     * Login type: "open" for PC QR code, "mp" for H5 web auth
     */
    private String type;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
