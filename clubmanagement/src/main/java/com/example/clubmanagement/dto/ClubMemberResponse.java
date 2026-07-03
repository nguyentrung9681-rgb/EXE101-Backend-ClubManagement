package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubMemberResponse {
    private Long id;
    private Long clubId;
    private String clubName;
    private Integer userId;
    private String fullName;
    private String email;
    private String role;
    private String status;
    private LocalDateTime joinedAt;
}
