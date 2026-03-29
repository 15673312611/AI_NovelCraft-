package com.novel.config;

import com.novel.util.JwtTokenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
 * 增强版 JWT 认证过滤器。
 * 优先从 token 中获取用户 ID，避免频繁查询数据库。
 */
@Component
public class EnhancedJwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedJwtAuthenticationFilter.class);
    private static final String RENEWED_TOKEN_HEADER = "X-Auth-Token";

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenUtil.isValidToken(jwt)) {
                String username = jwtTokenUtil.getUsernameFromToken(jwt);
                Long userId = jwtTokenUtil.getUserIdFromToken(jwt);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null
                        && jwtTokenUtil.validateToken(jwt, username)) {
                    EnhancedUserPrincipal principal = new EnhancedUserPrincipal(userId, username);
                    UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    issueRenewedToken(response, userId, username);
                    logger.debug("Enhanced JWT auth success: userId={}, username={}", userId, username);
                }
            }
        } catch (Exception ex) {
            logger.error("Enhanced JWT auth failed", ex);
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

    private void issueRenewedToken(HttpServletResponse response, Long userId, String username) {
        String renewedToken = userId != null
            ? jwtTokenUtil.generateToken(userId, username)
            : jwtTokenUtil.generateToken(username);
        response.setHeader(RENEWED_TOKEN_HEADER, renewedToken);
    }

    /**
     * Enhanced user principal with embedded user ID.
     */
    public static class EnhancedUserPrincipal implements UserDetails {
        private final Long userId;
        private final String username;

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
            return null;
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
