package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubResponse {
    private Integer id;
    private String name;
    private String description;
    private String logoUrl;
    private String status;
    private String visibility;
    private Integer createdByUserId;
    private String createdByName;
    private List<UserSummary> deletionPermittedUsers;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class UserSummary {
        private Integer userId;
        private String fullName;
        private String email;
    }
}
