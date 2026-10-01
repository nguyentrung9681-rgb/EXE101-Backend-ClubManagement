package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.GoogleCalendarService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/google")
public class GoogleOAuthController {

    private final GoogleCalendarService googleCalendarService;
    private final String frontendCalendarUrl;

    public GoogleOAuthController(
            GoogleCalendarService googleCalendarService,
            @Value("${app.frontend.calendar-url}") String frontendCalendarUrl) {
        this.googleCalendarService = googleCalendarService;
        this.frontendCalendarUrl = frontendCalendarUrl;
    }

    /**
     * Lấy URL kết nối tài khoản Google Calendar.
     * GET /api/google/connect?userId=1
     */
    @GetMapping("/connect")
    public ResponseEntity<?> connect(@RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            String url = googleCalendarService.getAuthorizeUrl(effectiveUserId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Callback nhận Authorization Code từ Google.
     * GET /api/google/callback?code=...&state=userId
     */
    @GetMapping("/callback")
    public void callback(@RequestParam String code, @RequestParam("state") Integer userId, HttpServletResponse response) throws IOException {
        try {
            googleCalendarService.exchangeCodeForTokens(userId, code);
            response.sendRedirect(frontendCalendarUrl + "?success=true");
        } catch (Exception e) {
            response.sendRedirect(frontendCalendarUrl + "?error=" + URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8));
        }
    }

    /**
     * Kiểm tra trạng thái liên kết tài khoản Google của người dùng.
     * GET /api/google/status?userId=1
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            Map<String, Object> status = googleCalendarService.getGoogleAccountStatus(effectiveUserId);
            return ResponseEntity.ok(status);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}