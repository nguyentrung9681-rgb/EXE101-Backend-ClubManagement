package com.example.clubmanagement.dto;

import com.example.clubmanagement.Entity.SheetFormType;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoogleSheetResponse {
    private Integer id;
    private String spreadsheetId;
    private String title;
    private SheetFormType type;
    private String spreadsheetUrl;
    private Integer userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
