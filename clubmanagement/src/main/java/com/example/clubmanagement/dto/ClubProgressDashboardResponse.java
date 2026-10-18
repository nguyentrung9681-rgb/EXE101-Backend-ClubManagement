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
    private long eventCriticalDoneTasks;
    private double eventProgressPercentage;
    private double overallProgressPercentage;
    private long overdueTasksCount;
}
