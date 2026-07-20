package com.example.clubmanagement.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubProgressDashboardResponse {
    private long totalTasks;
    private long todoTasks;
    private long doingTasks;
    private long doneTasks;
    private long financeRelatedTasks;
    private long eventCriticalTasks;
    private double eventProgressPercentage;
    private long overdueTasksCount;
}
