package com.dylincode.mcp.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * OAuth filter
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private WhiteListConfig whiteListConfig;

    // 從 yml 讀白名單
    @Bean
    AuthorizationManager<RequestAuthorizationContext> whitelistEmailAuthzManager() {

        return (authenticationSupplier, ctx) -> {
            // 檢查是否為本機請求
            String remoteAddr = ctx.getRequest().getRemoteAddr();
            if ("127.0.0.1".equals(remoteAddr) || "0:0:0:0:0:0:0:1".equals(remoteAddr) || "localhost".equals(remoteAddr)) {
                return new AuthorizationDecision(true); // 本機請求直接放行
            }

            Authentication auth = authenticationSupplier.get();

            // 未驗證 → 拒絕
            if (!(auth instanceof JwtAuthenticationToken jwtAuth) || !auth.isAuthenticated()) {
                return new AuthorizationDecision(false);
            }

            Jwt jwt = jwtAuth.getToken();
            String email = jwt.getClaim("email");

            boolean allowed = email != null
                    && (whiteListConfig.getEmails().contains(email.toLowerCase()) || whiteListConfig.getDomains().contains(extractDomain(email)));

            return new AuthorizationDecision(allowed);
        };
    }

    public static String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        return email.substring(email.indexOf("@") + 1);
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http,
                            AuthorizationManager<RequestAuthorizationContext> whitelistEmailAuthzManager) throws Exception {

        http
//                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/.well-known/**").permitAll()
                        .requestMatchers("/api/public/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .anyRequest().access(whitelistEmailAuthzManager)
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt()); // 驗簽 Google ID Token

        return http.build();
    }
}


