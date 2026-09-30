package com.example.clubmanagement.Controller;

import com.example.clubmanagement.dto.*;
import com.example.clubmanagement.Service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // CASE 2: Đăng ký tài khoản Local (Nhập tay thông tin)
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody RegisterRequest registerRequest) {
        try {
            String message = authService.registerLocal(registerRequest);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CASE 1: Đăng nhập tài khoản Local (Khi đã có sẵn tài khoản)
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginRequest loginRequest) {
        try {
            AuthResponse response = authService.loginLocal(loginRequest);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CASE 3: Đăng ký / Đăng nhập bằng tài khoản Google (Gmail)
    @PostMapping("/google")
    public ResponseEntity<?> googleAuth(@RequestBody GoogleLoginRequest googleLoginRequest) {
        try {
            AuthResponse response = authService.processGoogleUser(
                    googleLoginRequest.getEmail(),
                    googleLoginRequest.getFullName(),
                    googleLoginRequest.getGoogleId(),
                    googleLoginRequest.getAvatarUrl()
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CASE 4: Xử lý Quên / Đặt lại Mật khẩu trực tiếp
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        try {
            String message = authService.forgotPassword(request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // CASE 5: Thực hiện Đặt lại Mật khẩu mới trực tiếp (Email/Username + Mật khẩu mới)
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            String message = authService.resetPassword(request);
            return ResponseEntity.ok(Map.of("message", message));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        return ResponseEntity.ok(Map.of(
                "message", "Đăng xuất thành công"
        ));
    }
}