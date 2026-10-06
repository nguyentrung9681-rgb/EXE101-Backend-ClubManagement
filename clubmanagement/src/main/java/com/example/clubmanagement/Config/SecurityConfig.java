package com.example.clubmanagement.Config;

import com.example.clubmanagement.dto.AuthResponse;
import com.example.clubmanagement.Service.AuthService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final AuthService authService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final String frontendRedirectUrl;

    public SecurityConfig(
            AuthService authService,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitingFilter rateLimitingFilter,
            @Value("${app.frontend.redirect-url:https://exe-ebon.vercel.app}") String frontendRedirectUrl) {
        this.authService = authService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitingFilter = rateLimitingFilter;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    @Bean
    public FilterRegistrationBean<RateLimitingFilter> rateLimitingFilterRegistration(RateLimitingFilter filter) {
        FilterRegistrationBean<RateLimitingFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        String cleanFrontendRedirectUrl = (frontendRedirectUrl != null && frontendRedirectUrl.endsWith("/"))
                ? frontendRedirectUrl.substring(0, frontendRedirectUrl.length() - 1)
                : frontendRedirectUrl;

        // Cấu hình các Allowed Origins cụ thể thay vì wildcard "*" để bảo mật CORS
        List<String> allowedOrigins = List.of(
                cleanFrontendRedirectUrl,
                "https://exe-ebon.vercel.app",
                "http://localhost:3000",
                "http://localhost:5173",
                "http://127.0.0.1:3000",
                "http://127.0.0.1:5173"
        );
        configuration.setAllowedOriginPatterns(allowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With", "Accept", "Origin", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentTypeOptions(contentType -> {}) // X-Content-Type-Options: nosniff
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .referrerPolicy(referrer -> referrer
                                .policy(org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                )
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**",
                                "/api/auth/**",
                                "/login/**",
                                "/oauth2/**",
                                "/api/google/callback",
                                "/api/trello/**",
                                "/api/documents/webhook",
                                "/api/packages/**",
                                "/api/payments/**"
                        ).permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(rateLimitingFilter, LogoutFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .oauth2Login(oauth2 -> oauth2
                        .successHandler((request, response, authentication) -> {
                            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

                            String email = oAuth2User.getAttribute("email");
                            String name = oAuth2User.getAttribute("name");
                            String googleId = oAuth2User.getAttribute("sub");
                            String picture = oAuth2User.getAttribute("picture");

                            AuthResponse authResponse =
                                    authService.processGoogleUser(email, name, googleId, picture);

                            StringBuilder redirectUrlBuilder = new StringBuilder(frontendRedirectUrl);
                            if (frontendRedirectUrl.contains("?")) {
                                redirectUrlBuilder.append("&token=");
                            } else {
                                redirectUrlBuilder.append("?token=");
                            }
                            redirectUrlBuilder.append(URLEncoder.encode(authResponse.getToken(), StandardCharsets.UTF_8));
                            if (authResponse.getUserId() != null) {
                                redirectUrlBuilder.append("&userId=").append(authResponse.getUserId());
                            }
                            if (authResponse.getFullName() != null) {
                                redirectUrlBuilder.append("&fullName=").append(URLEncoder.encode(authResponse.getFullName(), StandardCharsets.UTF_8));
                            }
                            if (authResponse.getEmail() != null) {
                                redirectUrlBuilder.append("&email=").append(URLEncoder.encode(authResponse.getEmail(), StandardCharsets.UTF_8));
                            }
                            if (authResponse.getAuthProvider() != null) {
                                redirectUrlBuilder.append("&authProvider=").append(URLEncoder.encode(authResponse.getAuthProvider(), StandardCharsets.UTF_8));
                            }
                            if (authResponse.getLastSelectedClubId() != null) {
                                redirectUrlBuilder.append("&lastSelectedClubId=").append(authResponse.getLastSelectedClubId());
                            }

                            response.sendRedirect(redirectUrlBuilder.toString());
                        })
                );

        return http.build();
    }
}