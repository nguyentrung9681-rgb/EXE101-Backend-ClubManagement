package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubDocumentRequest {
    private Integer clubId;
    private Integer eventId; // nullable
    private String title;
    private String documentType; // EVENT, CLUB_ACTIVITY
}
