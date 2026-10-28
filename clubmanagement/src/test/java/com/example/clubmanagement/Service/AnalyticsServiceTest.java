package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubSubscription;
import com.example.clubmanagement.Entity.PaymentOrder;
import com.example.clubmanagement.Entity.SubscriptionPackage;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import com.example.clubmanagement.Repository.ClubSubscriptionRepository;
import com.example.clubmanagement.Repository.PaymentOrderRepository;
import com.example.clubmanagement.Repository.TrackingEventRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.AdminAnalyticsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    @Mock
    private TrackingEventRepository eventRepository;

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubSubscriptionRepository subscriptionRepository;

    @InjectMocks
    private AnalyticsService analyticsService;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    private static Object[] eventRow(String name, String sessionId, Integer userId,
                                     String source, String medium, String campaign) {
        return new Object[]{name, sessionId, userId, source, medium, campaign, null};
    }

    private static SubscriptionPackage pkg(int id, String code, int durationDays) {
        SubscriptionPackage p = new SubscriptionPackage();
        p.setId(id);
        p.setCode(code);
        p.setName(code);
        p.setDurationDays(durationDays);
        return p;
    }

    private static PaymentOrder paidOrder(long orderCode, BigDecimal amount, Integer userId) {
        User buyer = new User();
        buyer.setUserId(userId);
        return PaymentOrder.builder()
                .orderCode(orderCode)
                .amount(amount)
                .status(OrderStatus.PAID)
                .paidAt(LocalDateTime.now().minusDays(2))
                .createdAt(LocalDateTime.now().minusDays(3))
                .user(buyer)
                .build();
    }

    private static ClubSubscription subscription(SubscriptionPackage pack, LocalDateTime endDate, Integer userId) {
        ClubSubscription sub = new ClubSubscription();
        sub.setSubscriptionPackage(pack);
        sub.setStatus(SubscriptionStatus.ACTIVE);
        sub.setStartDate(endDate.minusDays(30));
        sub.setEndDate(endDate);
        if (userId != null) {
            User buyer = new User();
            buyer.setUserId(userId);
            sub.setOrder(PaymentOrder.builder().orderCode(9000L + userId).user(buyer).build());
        }
        return sub;
    }

    @Test
    @DisplayName("Đếm visitor theo từng bước phễu và quy doanh thu về kênh UTM first-touch")
    void funnelAndChannelsUseFirstTouchUtm() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(eventRow(AnalyticsService.PAGE_VIEW, "s1", 7, "facebook", "cpc", "oct"));
        rows.add(eventRow(AnalyticsService.PRICING_VIEW, "s1", 7, "facebook", "cpc", "oct"));
        rows.add(eventRow(AnalyticsService.PAGE_VIEW, "s2", null, null, null, null));
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(rows);
        when(eventRepository.countInRange(any(), any())).thenReturn(3L);

        List<PaymentOrder> paid = List.of(paidOrder(1001L, new BigDecimal("99000"), 7));
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(paid);
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any())).thenReturn(paid);
        when(userRepository.countByCreatedAtBetween(any(), any())).thenReturn(4L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        AdminAnalyticsResponse response = analyticsService.getAnalytics(30);

        assertEquals(2L, response.getFunnel().get(0).getCount(), "2 khách xem trang");
        assertEquals(1L, response.getFunnel().get(1).getCount(), "1 khách xem bảng giá");
        assertEquals(Double.valueOf(50.0), response.getFunnel().get(1).getConversionFromStart());
        assertEquals(4L, response.getFunnel().get(2).getCount(), "signup lấy từ bảng users");

        assertEquals(1, response.getChannels().size());
        AdminAnalyticsResponse.ChannelStat channel = response.getChannels().get(0);
        assertEquals("facebook", channel.getSource());
        assertEquals("cpc", channel.getMedium());
        assertEquals(1L, channel.getVisitors());
        assertEquals(1L, channel.getPaidOrders());
        assertEquals(0, new BigDecimal("99000").compareTo(channel.getRevenue()));

        assertEquals(0, new BigDecimal("99000").compareTo(response.getTotals().getRevenue()));
        assertEquals(3L, response.getTotals().getTrackedEvents());
    }

    @Test
    @DisplayName("Thẻ doanh thu: tổng theo paid_at, tăng trưởng 0% khi kỳ trước bằng kỳ này")
    void revenueCardsSumPaidOrders() {
        List<PaymentOrder> paid = List.of(
                paidOrder(1001L, new BigDecimal("99000"), 7),
                paidOrder(1002L, new BigDecimal("708000"), 8));
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any())).thenReturn(paid);
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        AdminAnalyticsResponse response = analyticsService.getAnalytics(30);
        AdminAnalyticsResponse.Revenue revenue = response.getRevenue();

        assertNotNull(revenue.getWeekly());
        assertNotNull(revenue.getMonthly());
        assertNotNull(revenue.getYearly());
        assertNotNull(revenue.getPeriod());
        assertEquals(0, new BigDecimal("807000").compareTo(revenue.getWeekly().getAmount()));
        assertEquals(2L, revenue.getMonthly().getPaidOrders());
        assertEquals(Double.valueOf(0.0), revenue.getWeekly().getGrowthPercent());
        assertEquals("Năm hiện tại", revenue.getYearly().getLabel());
        assertTrue(revenue.getYearly().getFrom().getDayOfYear() == 1, "thẻ năm chốt mốc 01/01");
    }

    @Test
    @DisplayName("Kỳ trước bằng kỳ này thì 0%, kỳ trước bằng 0 đồng thì growthPercent = null")
    void revenueGrowthIsNullWhenPreviousPeriodEmpty() {
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(List.of(paidOrder(1003L, new BigDecimal("99000"), 7)));
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        // Cùng một stub cho mọi cửa sổ nên kỳ này và kỳ trước trùng nhau -> tăng trưởng 0%.
        AdminAnalyticsResponse response = analyticsService.getAnalytics(7);
        assertNotNull(response.getRevenue().getWeekly().getAmount());
        assertEquals(Double.valueOf(0.0), response.getRevenue().getWeekly().getGrowthPercent());

        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(Collections.emptyList());
        AdminAnalyticsResponse empty = analyticsService.getAnalytics(7);
        assertEquals(0, BigDecimal.ZERO.compareTo(empty.getRevenue().getWeekly().getAmount()));
        assertNull(empty.getRevenue().getWeekly().getGrowthPercent());
    }

    @Test
    @DisplayName("Phân loại gói đang hiệu lực: >= 300 ngày tính là gói năm")
    void subscriptionsSplitMonthlyAndYearly() {
        SubscriptionPackage monthly = pkg(1, "MONTHLY", 30);
        SubscriptionPackage flash = pkg(3, "FLASH", 7);
        SubscriptionPackage yearly = pkg(2, "YEARLY", 365);
        LocalDateTime soon = LocalDateTime.now().plusDays(10);

        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(List.of(
                subscription(monthly, soon, 11),
                subscription(flash, LocalDateTime.now().plusDays(90), 11),
                subscription(yearly, LocalDateTime.now().plusDays(300), 12)));
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);

        AdminAnalyticsResponse.Subscriptions stats = analyticsService.getAnalytics(30).getSubscriptions();

        assertEquals(3L, stats.getTotalActive());
        assertEquals(2L, stats.getActiveMonthly());
        assertEquals(1L, stats.getActiveYearly());
        assertEquals(1L, stats.getExpiringIn30Days(), "chỉ gói hết hạn trong 30 ngày tới");
        assertEquals(2L, stats.getPayingUsers(), "user 11 trả cho 2 gói, user 12 trả cho 1 gói");
    }

    @Test
    @DisplayName("Trend doanh thu: theo ngày cho cửa sổ ngắn, theo tháng cho cửa sổ cả năm")
    void revenueTrendSwitchesGranularity() {
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(List.of(paidOrder(1004L, new BigDecimal("99000"), 7)));
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        AdminAnalyticsResponse monthlyWindow = analyticsService.getAnalytics(30);
        assertEquals("DAY", monthlyWindow.getTrendGranularity());
        assertEquals(31, monthlyWindow.getRevenueTrend().size(), "31 bucket ngày, tính cả ngày cuối");
        BigDecimal dailyTotal = monthlyWindow.getRevenueTrend().stream()
                .map(AdminAnalyticsResponse.TrendPoint::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("99000").compareTo(dailyTotal));

        AdminAnalyticsResponse yearlyWindow = analyticsService.getAnalytics(365);
        assertEquals("MONTH", yearlyWindow.getTrendGranularity());
        assertTrue(yearlyWindow.getRevenueTrend().size() >= 12 && yearlyWindow.getRevenueTrend().size() <= 13,
                "bucket theo tháng phủ kín cửa sổ 365 ngày");
    }
}
