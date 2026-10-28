package com.example.clubmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Du lieu cho tab Analytics cua trang quan tri: phin chuyen doi + phan bo kenh UTM. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAnalyticsResponse {

    private Integer days;
    private LocalDateTime from;
    private LocalDateTime to;
    private Totals totals;
    private List<FunnelStep> funnel;
    private List<ChannelStat> channels;
    private Revenue revenue;
    private List<TrendPoint> revenueTrend;
    /** DAY hoặc MONTH — độ phân giải của revenueTrend. */
    private String trendGranularity;
    private Subscriptions subscriptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Totals {
        private long trackedEvents;
        private long visitors;
        private long newUsers;
        private long checkouts;
        private long paidOrders;
        private BigDecimal revenue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Revenue {
        private RevenueCard weekly;
        private RevenueCard monthly;
        private RevenueCard yearly;
        /** Doanh thu đúng theo cửa sổ `days` của request. */
        private RevenueCard period;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueCard {
        private String label;
        private LocalDateTime from;
        private LocalDateTime to;
        private BigDecimal amount;
        private long paidOrders;
        private BigDecimal previousAmount;
        /** % tăng trưởng so với kỳ liền trước; null khi kỳ trước = 0 (không chia được). */
        private Double growthPercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        /** "2026-10-21" khi granularity=DAY, "2026-09" khi granularity=MONTH. */
        private String bucket;
        /** Nhãn ngắn để vẽ trục hoành: "21/10" hoặc "09/2026". */
        private String label;
        private BigDecimal amount;
        private long paidOrders;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Subscriptions {
        private long activeMonthly;
        private long activeYearly;
        private long totalActive;
        private long expiringIn30Days;
        private long payingUsers;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunnelStep {
        private String step;
        private String label;
        /** "visitor" | "user" | "order" - can de UI ghi chu thich dung. */
        private String unit;
        private long count;
        private Double conversionFromPrevious;
        private Double conversionFromStart;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelStat {
        private String source;
        private String medium;
        private long visitors;
        private long checkouts;
        private long paidOrders;
        private BigDecimal revenue;
    }
}
