package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AdminClubResponse {
    private Integer id;
    private String name;
    private String description;
    private String logoUrl;
    private String visibility; // PUBLIC, PRIVATE
    private String status;     // ACTIVE, INACTIVE
    private Integer managerId;
    private String managerName;
    private String managerEmail;
    private Long memberCount;
    private Long documentCount;
    private Long eventCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
