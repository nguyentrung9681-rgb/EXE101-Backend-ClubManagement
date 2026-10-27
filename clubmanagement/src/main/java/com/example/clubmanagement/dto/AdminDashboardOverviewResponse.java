package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AdminDashboardOverviewResponse {
    private DashboardStats stats;
    private List<MonthlyResourceStat> monthlyResourceUsage;
    private List<AdminClubResponse> currentClubs;
    private List<RecentActivityResponse> recentActivities;

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class DashboardStats {
        private Long totalUsers;
        private Long activeUsers;
        private Long bannedUsers;
        private Long totalClubs;
        private Long activeClubs;
        private Long publicClubs;
        private Long privateClubs;
        private Long totalDocuments;
        private Long totalEvents;
        private Long totalForms;
        private Long totalSheets;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class MonthlyResourceStat {
        private String month;       // "Jan", "Feb", "Mar", ...
        private Integer monthValue; // 1 - 12
        private Integer year;       // 2026
        private Long documents;
        private Long events;
        private Long forms;
        private Long sheets;
    }

    @Getter @Setter
    @NoArgsConstructor @AllArgsConstructor
    @Builder
    public static class RecentActivityResponse {
        private String id;
        private String actorName;
        private String actorAvatar;
        private String action;      // "created a new club", "created a Google Document", "created an event", "registered an account", "connected Trello"
        private String targetName;  // "Dog Club", "Meeting Minutes", etc.
        private String type;        // "CLUB", "DOCUMENT", "EVENT", "USER", "TRELLO"
        private LocalDateTime timestamp;
        private String timeAgo;     // "10 minutes ago", "2 hours ago", etc.
    }
}
