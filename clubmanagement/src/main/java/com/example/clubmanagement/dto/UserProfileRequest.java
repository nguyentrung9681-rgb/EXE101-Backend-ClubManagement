package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UserProfileRequest {
    private String fullName;
    private String username;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
}
