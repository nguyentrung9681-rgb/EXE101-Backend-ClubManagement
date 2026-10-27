package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AdminUserResponse {
    private Integer userId;
    private String fullName;
    private String username;
    private String email;
    private String phoneNumber;
    private String avatarUrl;
    private String authProvider;
    private String userStatus;
    private LocalDateTime createdAt;
    private Integer joinedClubsCount;
    private Integer managedClubsCount;
    private List<UserClubMembershipInfo> clubs;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UserClubMembershipInfo {
        private Integer clubId;
        private String clubName;
        private String role;
        private String status;
        private String departmentName;
    }
}
