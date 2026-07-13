package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DocumentRevisionResponse {
    private Integer id;
    private Integer version;
    private String content;
    private LocalDateTime syncedAt;
}
