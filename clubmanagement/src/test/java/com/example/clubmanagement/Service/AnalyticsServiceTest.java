package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubSubscription;
import com.example.clubmanagement.Entity.PaymentOrder;
import com.example.clubmanagement.Entity.SubscriptionPackage;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import com.example.clubmanagement.Repository.ClubSubscriptionRepository;
import com.example.clubmanagement.Repository.FeedbackRepository;
import com.example.clubmanagement.Repository.PaymentOrderRepository;
import com.example.clubmanagement.Repository.TrackingEventRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.AdminAnalyticsResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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

    @Mock
    private FeedbackRepository feedbackRepository;

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

    private static PaymentOrder packageOrder(long orderCode, BigDecimal amount, Integer clubId, SubscriptionPackage pack) {
        Club club = new Club();
        club.setId(clubId);
        PaymentOrder order = paidOrder(orderCode, amount, clubId);
        order.setClub(club);
        order.setSubscriptionPackage(pack);
        return order;
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

    @ParameterizedTest
    @CsvSource({
            "0, 20, 0.0, false",
            "12, 8, 60.0, false",
            "19, 1, 95.0, false",
            "20, 0, 100.0, true",
            "25, 0, 125.0, true"
    })
    void transactionGoalTracksTwentyPaidOrders(long paidOrders, long remainingOrders,
                                               double progressPercent, boolean achieved) {
        when(orderRepository.countPaidOrdersForTransactionGoal()).thenReturn(paidOrders);

        AdminAnalyticsResponse.TransactionGoal goal = analyticsService.getAnalytics(30).getTransactionGoal();

        assertNotNull(goal);
        assertEquals(20L, goal.getTargetOrders());
        assertEquals(paidOrders, goal.getPaidOrders());
        assertEquals(remainingOrders, goal.getRemainingOrders());
        assertEquals(progressPercent, goal.getProgressPercent());
        assertEquals(achieved, goal.isAchieved());
    }

    @Test
    void transactionGoalUsesLifetimeCountInsteadOfPeriodTotals() {
        when(orderRepository.countPaidOrdersForTransactionGoal()).thenReturn(20L);
        List<PaymentOrder> paid = List.of(paidOrder(1001L, new BigDecimal("99000"), 7));
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any())).thenReturn(paid);

        AdminAnalyticsResponse weekly = analyticsService.getAnalytics(7);
        AdminAnalyticsResponse yearly = analyticsService.getAnalytics(365);

        assertEquals(1L, weekly.getTotals().getPaidOrders());
        assertEquals(20L, weekly.getTransactionGoal().getPaidOrders());
        assertTrue(weekly.getTransactionGoal().isAchieved());
        assertEquals(weekly.getTransactionGoal(), yearly.getTransactionGoal());
    }

    @Test
    void transactionGoalRefreshesCountOnEachRequest() {
        when(orderRepository.countPaidOrdersForTransactionGoal()).thenReturn(19L, 20L);

        assertEquals(1L, analyticsService.getAnalytics(30).getTransactionGoal().getRemainingOrders());
        assertTrue(analyticsService.getAnalytics(30).getTransactionGoal().isAchieved());
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
        when(userRepository.findIdsCreatedAtBetween(any(), any())).thenReturn(List.of(7, 8, 9, 10));
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
    @DisplayName("Kênh: quy doanh thu về first-touch ngoài cửa sổ + AOV/conversion/newUsers")
    void channelAttributionUsesAllTimeFirstTouch() {
        // Không có event UTM trong cửa sổ cho user 7 (họ đến từ kỳ trước)
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        // First-touch mọi thời điểm vẫn trả về facebook cho user 7
        when(eventRepository.findFirstTouchByUserIds(any())).thenReturn(List.<Object[]>of(
                new Object[]{7, "facebook", "cpc", "oct"}));

        List<PaymentOrder> paid = List.of(
                paidOrder(1001L, new BigDecimal("99000"), 7),
                paidOrder(1002L, new BigDecimal("198000"), 7));
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any())).thenReturn(paid);
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(userRepository.findIdsCreatedAtBetween(any(), any())).thenReturn(List.of(7));
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        AdminAnalyticsResponse response = analyticsService.getAnalytics(30);

        assertEquals(1, response.getChannels().size(), "kênh facebook vẫn hiện dù không có visitor trong cửa sổ");
        AdminAnalyticsResponse.ChannelStat channel = response.getChannels().get(0);
        assertEquals("facebook", channel.getSource());
        assertEquals(0L, channel.getVisitors());
        assertEquals(1L, channel.getNewUsers(), "user 7 mới quy về facebook");
        assertEquals(2L, channel.getPaidOrders());
        assertEquals(0, new BigDecimal("297000").compareTo(channel.getRevenue()));
        assertEquals(0, new BigDecimal("148500").compareTo(channel.getAverageOrderValue()), "297000 / 2 đơn");
        assertNull(channel.getConversionRate(), "không có visitor trong cửa sổ -> không tính conversion");
        assertEquals(0, new BigDecimal("148500").compareTo(response.getTotals().getAverageOrderValue()));
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
    @DisplayName("Đơn bán gói theo kỳ: tách theo gói, mua mới vs gia hạn, sắp xếp theo doanh thu")
    void subscriptionSalesSplitsByPackageAndRenewal() {
        SubscriptionPackage monthly = pkg(1, "MONTHLY", 30);
        SubscriptionPackage yearly = pkg(2, "YEARLY", 365);

        List<PaymentOrder> paid = List.of(
                packageOrder(2001L, new BigDecimal("99000"), 10, monthly),
                packageOrder(2002L, new BigDecimal("99000"), 10, monthly),
                packageOrder(2003L, new BigDecimal("708000"), 20, yearly));
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any())).thenReturn(paid);
        when(orderRepository.findClubIdsPaidBefore(any())).thenReturn(List.of(20));
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        AdminAnalyticsResponse.SubscriptionSales sales = analyticsService.getAnalytics(30).getSubscriptionSales();

        assertEquals(3L, sales.getPaidOrders());
        assertEquals(0, new BigDecimal("906000").compareTo(sales.getRevenue()));
        assertEquals(2L, sales.getPayingClubs());
        assertEquals(1L, sales.getNewSubscriptions(), "CLB 10 chưa từng trả tiền trước kỳ này");
        assertEquals(2L, sales.getRenewals(), "đơn thứ 2 của CLB 10 và đơn của CLB 20 là gia hạn");

        assertEquals(2, sales.getByPackage().size());
        AdminAnalyticsResponse.PackageSales top = sales.getByPackage().get(0);
        assertEquals("YEARLY", top.getCode(), "gói doanh thu cao nhất xếp trước");
        assertEquals("YEARLY", top.getCycle());
        assertEquals(365, top.getDurationDays());
        assertEquals(1L, top.getPaidOrders());
        assertEquals(0L, top.getNewSubscriptions());
        assertEquals(1L, top.getRenewals());

        AdminAnalyticsResponse.PackageSales monthlySales = sales.getByPackage().get(1);
        assertEquals("MONTHLY", monthlySales.getCycle());
        assertEquals(2L, monthlySales.getPaidOrders());
        assertEquals(0, new BigDecimal("198000").compareTo(monthlySales.getRevenue()));
        assertEquals(1L, monthlySales.getNewSubscriptions());
        assertEquals(1L, monthlySales.getRenewals());
    }

    @Test
    @DisplayName("Kỳ không có đơn nào: subscriptionSales trả 0 và danh sách gói rỗng")
    void subscriptionSalesEmptyWhenNoPaidOrders() {
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findClubIdsPaidBefore(any())).thenReturn(Collections.emptyList());
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        AdminAnalyticsResponse.SubscriptionSales sales = analyticsService.getAnalytics(7).getSubscriptionSales();

        assertEquals(0L, sales.getPaidOrders());
        assertEquals(0L, sales.getNewSubscriptions());
        assertEquals(0L, sales.getRenewals());
        assertEquals(0, BigDecimal.ZERO.compareTo(sales.getRevenue()));
        assertTrue(sales.getByPackage().isEmpty());
    }

    @Test
    @DisplayName("Reviews: coverage trên khách trả tiền và tỉ lệ mua lại, tổng user toàn hệ thống")
    void reviewsCoverageAndRepeatPurchase() {
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());

        when(userRepository.count()).thenReturn(40L);
        when(feedbackRepository.count()).thenReturn(8L);
        when(feedbackRepository.countByOrderIsNotNull()).thenReturn(6L);
        when(feedbackRepository.findAverageRating()).thenReturn(4.333);
        when(orderRepository.countDistinctPayingCustomers()).thenReturn(12L);
        when(orderRepository.findRepeatPayingCustomerIds()).thenReturn(List.of(1, 2, 3));

        AdminAnalyticsResponse response = analyticsService.getAnalytics(30);
        AdminAnalyticsResponse.Reviews reviews = response.getReviews();

        assertEquals(40L, response.getTotals().getTotalUsers());
        assertEquals(12L, response.getTotals().getPayingCustomers());
        assertEquals(8L, reviews.getTotalReviews());
        assertEquals(6L, reviews.getVerifiedReviews());
        assertEquals(Double.valueOf(4.33), reviews.getAverageRating());
        assertEquals(Double.valueOf(50.0), reviews.getReviewCoveragePercent(), "6 verified / 12 khách trả tiền");
        assertEquals(3L, reviews.getRepeatPayingCustomers());
        assertEquals(Double.valueOf(25.0), reviews.getRepeatPurchaseRate(), "3 / 12 khách mua lại");
    }

    @Test
    @DisplayName("Chưa có khách trả tiền: coverage và repeat rate là null (không chia cho 0)")
    void reviewsNullRatesWhenNoPayingCustomers() {
        when(orderRepository.findByStatusAndPaidAtBetween(eq(OrderStatus.PAID), any(), any()))
                .thenReturn(Collections.emptyList());
        when(orderRepository.findByCreatedAtBetween(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.findFunnelRows(any(), any())).thenReturn(Collections.emptyList());
        when(eventRepository.countInRange(any(), any())).thenReturn(0L);
        when(subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE)).thenReturn(Collections.emptyList());
        when(orderRepository.countDistinctPayingCustomers()).thenReturn(0L);
        when(feedbackRepository.findAverageRating()).thenReturn(null);

        AdminAnalyticsResponse.Reviews reviews = analyticsService.getAnalytics(30).getReviews();

        assertNull(reviews.getReviewCoveragePercent());
        assertNull(reviews.getRepeatPurchaseRate());
        assertNull(reviews.getAverageRating());
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
