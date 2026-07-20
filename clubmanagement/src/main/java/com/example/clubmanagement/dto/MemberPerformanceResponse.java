package com.example.clubmanagement.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberPerformanceResponse {
    private Integer userId;
    private String fullName;
    private String email;
    private long assignedTasksCount;
    private long completedTasksCount;
    private long inProgressTasksCount;
    private long overdueTasksCount;
}
