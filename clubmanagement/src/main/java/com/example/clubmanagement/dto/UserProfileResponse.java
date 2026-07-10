package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserProfileResponse {
    private Integer userId;
    private String fullName;
    private String username;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String authProvider;
    private String userStatus;
    private LocalDateTime createdAt;
}
