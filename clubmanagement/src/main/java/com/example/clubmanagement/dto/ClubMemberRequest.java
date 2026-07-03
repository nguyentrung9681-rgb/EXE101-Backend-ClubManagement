package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubMemberRequest {
    private Integer userId;
    private String role; // PRESIDENT, TREASURER, MEMBER
    private String status; // ACTIVE, PENDING, LEFT
}
