package com.example.clubmanagement.dto;

import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoogleFormQuestionRequest {
    private String title;
    private String type; // TEXT, PARAGRAPH, MULTIPLE_CHOICE, CHECKBOX
    private Boolean required;
    private List<String> options;
}
