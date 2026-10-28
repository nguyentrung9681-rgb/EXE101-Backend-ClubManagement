package com.example.clubmanagement.Config;

import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserRepository userRepository;
    private final java.util.Set<String> adminEmails;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                  UserRepository userRepository,
                                  @org.springframework.beans.factory.annotation.Value("${app.admin-emails:}") String adminEmailsCsv) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
        this.adminEmails = java.util.Arrays.stream(adminEmailsCsv.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(java.util.stream.Collectors.toSet());
    }

    private boolean isAdmin(User user) {
        if (user.getRole() == com.example.clubmanagement.Enum.SystemRole.ADMIN) {
            return true;
        }
        if (adminEmails.isEmpty()) {
            return false;
        }
        return (user.getEmail() != null && adminEmails.contains(user.getEmail().toLowerCase(java.util.Locale.ROOT)))
                || (user.getUsername() != null && adminEmails.contains(user.getUsername().toLowerCase(java.util.Locale.ROOT)));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromJWT(jwt);

                User user = userRepository.findByUsername(username)
                        .or(() -> userRepository.findByEmail(username))
                        .orElse(null);

                if (user != null && !"BANNED".equals(user.getUserStatus()) && !"INACTIVE".equals(user.getUserStatus())) {
                    java.util.List<SimpleGrantedAuthority> authorities = isAdmin(user)
                            ? java.util.List.of(
                                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                                    new SimpleGrantedAuthority("ROLE_USER"))
                            : java.util.List.of(
                                    new SimpleGrantedAuthority("ROLE_USER"));
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            user, null, authorities);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication in security context", ex);
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
}
