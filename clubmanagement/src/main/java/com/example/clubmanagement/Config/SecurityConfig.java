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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AuthService authService;
    private final String frontendRedirectUrl;

    public SecurityConfig(
            AuthService authService,
            @Value("${app.frontend.redirect-url:https://exe-ebon.vercel.app/oauth2/redirect}") String frontendRedirectUrl) {
        this.authService = authService;
        this.frontendRedirectUrl = frontendRedirectUrl;
    }

    @Bean
    public org.springframework.web.cors.CorsConfigurationSource corsConfigurationSource() {
        org.springframework.web.cors.CorsConfiguration configuration = new org.springframework.web.cors.CorsConfiguration();
        configuration.setAllowedOrigins(java.util.List.of("*"));
        configuration.setAllowedMethods(java.util.List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(java.util.List.of("*"));
        org.springframework.web.cors.UrlBasedCorsConfigurationSource source = new org.springframework.web.cors.UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
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
                                "/api/google/**",
                                "/api/clubs/**",
                                "/api/events/**",
                                "/api/users/**",
                                "/api/documents/**",
                                "/api/trello/**",
                                "/api/tasks/**",
                                "/api/trello/callback",
                                "/api/trello/webhook/**"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
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
                            redirectUrlBuilder.append("?token=").append(URLEncoder.encode(authResponse.getToken(), StandardCharsets.UTF_8));
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