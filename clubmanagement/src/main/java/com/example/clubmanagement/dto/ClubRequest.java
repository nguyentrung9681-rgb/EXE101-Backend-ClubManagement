package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubRequest {
    private String name;
    private String description;
    private String logoUrl;
    private String visibility;
}
