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
    /** Đơn thanh toán gói membership trong cửa sổ `days`, tách theo từng gói. */
    private SubscriptionSales subscriptionSales;
    /** Review và giữ chân khách hàng (G3.4): số review, verified, điểm trung bình, coverage, mua lại. */
    private Reviews reviews;
    private TransactionGoal transactionGoal;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransactionGoal {
        private long targetOrders;
        private long paidOrders;
        private long remainingOrders;
        private double progressPercent;
        private boolean achieved;
    }

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
        /** Tổng số tài khoản toàn hệ thống (không chỉ trong cửa sổ `days`). */
        private long totalUsers;
        /** Khách đã từng thanh toán, đếm một lần cho mỗi người (mọi thời điểm). */
        private long payingCustomers;
        /** Giá trị đơn trung bình trong cửa sổ `days` = revenue / paidOrders; null khi chưa có đơn. */
        private BigDecimal averageOrderValue;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reviews {
        private long totalReviews;
        /** Review gắn với một đơn PAID (verified). */
        private long verifiedReviews;
        /** Điểm trung bình 1-5, null khi chưa có review. */
        private Double averageRating;
        /** Khách đã từng trả tiền (mẫu số của coverage). */
        private long payingCustomers;
        /** % khách đã trả tiền có để lại review verified; null khi chưa có khách trả tiền. */
        private Double reviewCoveragePercent;
        /** Khách có từ 2 đơn PAID trở lên. */
        private long repeatPayingCustomers;
        /** % khách trả tiền mua lại; null khi chưa có khách trả tiền. */
        private Double repeatPurchaseRate;
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
    public static class SubscriptionSales {
        private LocalDateTime from;
        private LocalDateTime to;
        private long paidOrders;
        private BigDecimal revenue;
        /** Đơn của CLB chưa từng thanh toán gói nào trước cửa sổ đang xem. */
        private long newSubscriptions;
        private long renewals;
        private long payingClubs;
        private List<PackageSales> byPackage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackageSales {
        private Integer packageId;
        private String code;
        private String name;
        private Integer durationDays;
        /** MONTHLY (dưới 300 ngày) hoặc YEARLY (từ 300 ngày). */
        private String cycle;
        private long paidOrders;
        private BigDecimal revenue;
        private long newSubscriptions;
        private long renewals;
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
        /** User mới trong cửa sổ `days` quy về kênh first-touch này. */
        private long newUsers;
        private long checkouts;
        private long paidOrders;
        private BigDecimal revenue;
        /** Tỉ lệ chuyển đổi visitor -> đơn trả tiền (%); null khi kênh chưa có visitor. */
        private Double conversionRate;
        /** Giá trị đơn trung bình của kênh = revenue / paidOrders; null khi chưa có đơn. */
        private BigDecimal averageOrderValue;
    }
}
