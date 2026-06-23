package com.example.clubmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor  // Constructor không tham số
//@AllArgsConstructor
public class AuthResponse {

    private String token;
    private Integer userId;
    private String fullName;
    private String username;
    private String email;
    private String authProvider;
    private Integer lastSelectedClubId; // Đáp ứng Case 1: ID Câu lạc bộ chọn gần nhất để điều hướng
    private String message;

    public AuthResponse(
            String token,
            Integer userId,
            String fullName,
            String username,
            String email,
            String authProvider,
            Integer lastSelectedClubId,
            String message
    ) {
        this.token=token;
        this.userId = userId;
        this.fullName = fullName;
        this.username = username;
        this.email = email;
        this.authProvider = authProvider;
        this.lastSelectedClubId=lastSelectedClubId;
        this.message = message;
    }

    public String getToken() {
        return token;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getFullName() {
        return fullName;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getAuthProvider() {
        return authProvider;
    }

    public Integer getLastSelectedClubId() {
        return lastSelectedClubId;
    }

    public String getMessage() {
        return message;
    }
}
