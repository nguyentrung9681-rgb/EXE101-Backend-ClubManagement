package com.example.clubmanagement.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubOverviewDashboardResponse {

    private Integer clubId;

    /** Tổng số thành viên ACTIVE của CLB */
    private long totalMembers;

    /** Tổng số task chưa hoàn thành (TODO + DOING) */
    private long unfinishedTasks;

    /** Biểu đồ tròn "Task progress" */
    private TaskProgress taskProgress;

    /** Năm đang thống kê quỹ */
    private int fundYear;

    /** Thu / chi quỹ theo từng tháng (12 phần tử, tháng 1 → 12) */
    private List<MonthlyFund> monthlyFund;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class TaskProgress {
        private long totalTasks;
        private long completedTasks;
        private long inProgressTasks;
        private long todoTasks;
        private double completedPercent;
        private double inProgressPercent;
        private double todoPercent;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class MonthlyFund {
        private int month;
        /** Tổng tiền tăng thêm (INCOME) */
        private BigDecimal totalIncome;
        /** Tổng tiền trừ đi (EXPENSE) */
        private BigDecimal totalExpense;
        /** Chênh lệch = income - expense */
        private BigDecimal net;
    }
}
