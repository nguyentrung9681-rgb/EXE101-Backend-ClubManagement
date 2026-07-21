package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubDocumentResponse {
    private Integer id;
    private Integer clubId;
    private String clubName;
    private Integer eventId;
    private String eventTitle;
    private String title;
    private String category;
    private String documentType;
    private String googleDocumentId;
    private String documentUrl;
    private String syncStatus;
    private String contentSummary;
    private Integer createdByUserId;
    private String createdByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
