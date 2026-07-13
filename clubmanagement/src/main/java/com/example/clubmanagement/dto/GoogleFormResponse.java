package com.example.clubmanagement.dto;

import com.example.clubmanagement.Entity.SheetFormType;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoogleFormResponse {
    private Integer id;
    private String formId;
    private String title;
    private SheetFormType type;
    private String formUrl;
    private String responderUri;
    private Integer userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
