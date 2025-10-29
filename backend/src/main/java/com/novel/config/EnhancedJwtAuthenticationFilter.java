package com.novel.config;

import com.novel.util.JwtTokenUtil;
import com.novel.service.CustomUserDetailsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 增强版JWT认证过滤器
 * 优先从Token中获取用户ID，避免频繁查询数据库
 * 
 * @author Novel Creation System
 * @version 2.0.0
 * @since 2024-10-22
 */
@Component
public class EnhancedJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedJwtAuthenticationFilter.class);

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    @Lazy
    private UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                  FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenUtil.isValidToken(jwt)) {
                String username = jwtTokenUtil.getUsernameFromToken(jwt);
                Long userId = jwtTokenUtil.getUserIdFromToken(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    
                    if (jwtTokenUtil.validateToken(jwt, username)) {
                        // 创建增强版用户主体，包含用户ID
                        EnhancedUserPrincipal principal = new EnhancedUserPrincipal(userId, username);
                        
                        UsernamePasswordAuthenticationToken authentication = 
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                        logger.debug("增强JWT认证成功: userId={}, username={}", userId, username);
                    }
                }
            }
        } catch (Exception ex) {
            logger.error("增强JWT认证失败", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 增强版用户主体，直接包含用户ID，避免查数据库
     */
    public static class EnhancedUserPrincipal implements UserDetails {
        private Long userId;
        private String username;

        public EnhancedUserPrincipal(Long userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public Long getUserId() {
            return userId;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return null; // JWT认证不需要密码
        }

        @Override
        public java.util.Collection<? extends org.springframework.security.core.GrantedAuthority> getAuthorities() {
            java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
            authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
            return authorities;
        }

        @Override
        public boolean isAccountNonExpired() {
            return true;
        }

        @Override
        public boolean isAccountNonLocked() {
            return true;
        }

        @Override
        public boolean isCredentialsNonExpired() {
            return true;
        }

        @Override
        public boolean isEnabled() {
            return true;
        }
    }
}
