package com.novel.admin;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

public class PasswordEncoderTest {

    @Test
    public void generatePassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        
        String rawPassword = "admin123";
        String encodedPassword = encoder.encode(rawPassword);
        
        System.out.println("原始密码: " + rawPassword);
        System.out.println("加密后密码: " + encodedPassword);
        System.out.println();
        System.out.println("SQL语句:");
        System.out.println("UPDATE users SET password = '" + encodedPassword + "' WHERE username = 'admin';");
        
        // 验证
        boolean matches = encoder.matches(rawPassword, encodedPassword);
        System.out.println("\n密码验证: " + (matches ? "成功" : "失败"));
    }
}
