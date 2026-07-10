package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.UserService;
import com.example.clubmanagement.dto.ChangePasswordRequest;
import com.example.clubmanagement.dto.UserProfileRequest;
import com.example.clubmanagement.dto.UserProfileResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Lấy thông tin cá nhân của người dùng.
     * GET /api/users/{userId}
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserProfile(@PathVariable Integer userId) {
        try {
            UserProfileResponse response = userService.getUserProfile(userId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Cập nhật thông tin cá nhân.
     * PUT /api/users/{userId}
     */
    @PutMapping("/{userId}")
    public ResponseEntity<?> updateUserProfile(@PathVariable Integer userId, @RequestBody UserProfileRequest request) {
        try {
            UserProfileResponse response = userService.updateUserProfile(userId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Thay đổi mật khẩu.
     * PUT /api/users/{userId}/change-password
     */
    @PutMapping("/{userId}/change-password")
    public ResponseEntity<?> changePassword(@PathVariable Integer userId, @RequestBody ChangePasswordRequest request) {
        try {
            userService.changePassword(userId, request);
            return ResponseEntity.ok(Map.of("message", "Thay đổi mật khẩu thành công!"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
