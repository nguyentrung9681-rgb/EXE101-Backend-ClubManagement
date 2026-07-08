package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.GoogleCalendarService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<?> connect(@RequestParam Integer userId) {
        try {
            String url = googleCalendarService.getAuthorizeUrl(userId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Callback nhận Authorization Code từ Google.
     * GET /api/google/callback?code=...&state=userId
     */
    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam String code, @RequestParam("state") Integer userId) {
        try {
            googleCalendarService.exchangeCodeForTokens(userId, code);
            return ResponseEntity.ok("<h1>Kết nối tài khoản Google thành công!</h1>" +
                    "<p>Bạn đã liên kết lịch hoạt động với Google Calendar. Trình duyệt đang chuyển hướng...</p>" +
                    "<script>" +
                    "setTimeout(() => {" +
                    "  window.location.href = \"" + frontendCalendarUrl + "\";" +
                    "}, 1500);" +
                    "</script>");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("<h1>Kết nối thất bại</h1><p>Đã xảy ra lỗi: " + e.getMessage() + "</p>");
        }
    }
}
