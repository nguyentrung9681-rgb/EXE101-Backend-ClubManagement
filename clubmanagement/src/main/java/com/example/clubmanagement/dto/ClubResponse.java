package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubResponse {
    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private String status;
    private Integer createdByUserId;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
