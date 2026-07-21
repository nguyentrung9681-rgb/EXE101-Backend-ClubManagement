package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DepartmentRequest {
    private String name;
    private String description;
    private Integer headMemberId;
}
