package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DepartmentResponse {
    private Integer id;
    private String name;
    private String description;
    private Integer clubId;
    private Integer headMemberId;
    private String headName;
    private LocalDateTime createdAt;
}
