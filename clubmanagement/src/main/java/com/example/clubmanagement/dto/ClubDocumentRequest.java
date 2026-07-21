package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubDocumentRequest {
    private Integer clubId;
    private Integer eventId; // nullable
    private String title;
    private String category; // EVENT, CLUB_ACTIVITY
    private String documentType; // MEETING_MINUTES, EVENT_PLAN, REPORT, FINANCE, OTHER
}
