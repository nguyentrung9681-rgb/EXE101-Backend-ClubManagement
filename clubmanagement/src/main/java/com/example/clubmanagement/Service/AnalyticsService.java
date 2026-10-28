package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubSubscription;
import com.example.clubmanagement.Entity.PaymentOrder;
import com.example.clubmanagement.Entity.TrackingEvent;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import com.example.clubmanagement.Repository.ClubSubscriptionRepository;
import com.example.clubmanagement.Repository.PaymentOrderRepository;
import com.example.clubmanagement.Repository.TrackingEventRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.AdminAnalyticsResponse;
import com.example.clubmanagement.dto.AnalyticsEventRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AnalyticsService {

    public static final String PAGE_VIEW = "page_view";
    public static final String PRICING_VIEW = "pricing_view";
    public static final String SIGNUP = "signup";
    public static final String CHECKOUT_START = "checkout_start";
    public static final String PAYMENT_SUCCESS = "payment_success";

    private static final Pattern EVENT_NAME = Pattern.compile("[a-z0-9_]{2,60}");
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("dd/MM");

    private final TrackingEventRepository eventRepository;
    private final PaymentOrderRepository orderRepository;
    private final UserRepository userRepository;
    private final ClubSubscriptionRepository subscriptionRepository;

    public AnalyticsService(TrackingEventRepository eventRepository,
                            PaymentOrderRepository orderRepository,
                            UserRepository userRepository,
                            ClubSubscriptionRepository subscriptionRepository) {
        this.eventRepository = eventRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    /** Endpoint cong khai: frontend gui hanh vi + UTM ve day. */
    @Transactional
    public void track(AnalyticsEventRequest request, Integer authenticatedUserId) {
        String eventName = clean(request.getEventName(), 60);
        if (eventName == null || !EVENT_NAME.matcher(eventName).matches()) {
            throw new IllegalArgumentException("eventName khong hop le (chi chu thuong, so, gach duoi, toi da 60 ky tu)");
        }

        eventRepository.save(TrackingEvent.builder()
                .eventName(eventName)
                .sessionId(clean(request.getSessionId(), 64))
                .userId(authenticatedUserId)
                .pagePath(clean(request.getPath(), 255))
                .pageReferrer(clean(request.getReferrer(), 500))
                .utmSource(clean(request.getUtmSource(), 80))
                .utmMedium(clean(request.getUtmMedium(), 80))
                .utmCampaign(clean(request.getUtmCampaign(), 120))
                .utmContent(clean(request.getUtmContent(), 120))
                .build());
    }

    /**
     * Goi tu dong thanh toan / dang ky. NOT_SUPPORTED tam ngung transaction cua dong thanh toan
     * de mot loi ghi de luu luong chi bi rollback rieng, khong bao gio lam mat don hang that.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void recordQuiet(String eventName, Integer userId, Integer clubId, Long orderCode,
                            BigDecimal amount, String utmSource, String utmMedium, String utmCampaign) {
        try {
            String source = clean(utmSource, 80);
            String medium = clean(utmMedium, 80);
            String campaign = clean(utmCampaign, 120);

            if (source == null && userId != null) {
                TrackingEvent firstTouch = eventRepository
                        .findFirstByUserIdAndUtmSourceIsNotNullOrderByCreatedAtAsc(userId)
                        .orElse(null);
                if (firstTouch != null) {
                    source = firstTouch.getUtmSource();
                    medium = firstTouch.getUtmMedium();
                    campaign = firstTouch.getUtmCampaign();
                }
            }

            eventRepository.save(TrackingEvent.builder()
                    .eventName(eventName)
                    .userId(userId)
                    .clubId(clubId)
                    .orderCode(orderCode)
                    .amount(amount)
                    .utmSource(source)
                    .utmMedium(medium)
                    .utmCampaign(campaign)
                    .build());
        } catch (Exception e) {
            log.warn("Khong ghi duoc tracking event '{}': {}", eventName, e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAnalytics(int days) {
        int window = Math.min(Math.max(days, 1), 365);
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusDays(window);

        List<Object[]> rows = eventRepository.findFunnelRows(from, to);
        long trackedEvents = eventRepository.countInRange(from, to);

        Map<String, Set<String>> visitorsByStep = new HashMap<>();
        Map<String, Set<String>> visitorsByChannel = new HashMap<>();
        Map<Integer, String> firstTouchByUser = new HashMap<>();
        Set<String> allVisitors = new HashSet<>();
        long anonymousIndex = 0;

        for (Object[] row : rows) {
            String eventName = (String) row[0];
            String sessionId = (String) row[1];
            Integer userId = (Integer) row[2];
            String source = (String) row[3];
            String medium = (String) row[4];
            BigDecimal amount = (BigDecimal) row[6];

            String visitorKey;
            if (sessionId != null && !sessionId.isBlank()) {
                visitorKey = "s:" + sessionId;
            } else if (userId != null) {
                visitorKey = "u:" + userId;
            } else {
                visitorKey = "e:" + (++anonymousIndex);
            }
            allVisitors.add(visitorKey);
            visitorsByStep.computeIfAbsent(eventName, k -> new HashSet<>()).add(visitorKey);

            if (source != null && !source.isBlank()) {
                String channelKey = channelKey(source, medium);
                visitorsByChannel.computeIfAbsent(channelKey, k -> new HashSet<>()).add(visitorKey);
                if (userId != null) {
                    firstTouchByUser.putIfAbsent(userId, channelKey);
                }
            }
        }

        List<PaymentOrder> createdOrders = orderRepository.findByCreatedAtBetween(from, to);
        List<PaymentOrder> paidOrders = orderRepository.findByStatusAndPaidAtBetween(OrderStatus.PAID, from, to);
        long newUsers = userRepository.countByCreatedAtBetween(from, to);

        List<TrendBucket> trend = buildTrend(from, to);
        String granularity = trend.isEmpty() || isDailyBucket(trend.get(0)) ? "DAY" : "MONTH";

        return AdminAnalyticsResponse.builder()
                .days(window)
                .from(from)
                .to(to)
                .totals(AdminAnalyticsResponse.Totals.builder()
                        .trackedEvents(trackedEvents)
                        .visitors(allVisitors.size())
                        .newUsers(newUsers)
                        .checkouts(createdOrders.size())
                        .paidOrders(paidOrders.size())
                        .revenue(sumAmount(paidOrders))
                        .build())
                .funnel(buildFunnel(visitorsByStep, newUsers, createdOrders.size(), paidOrders.size()))
                .channels(buildChannels(visitorsByChannel, firstTouchByUser, createdOrders, paidOrders))
                .revenue(buildRevenue(from, to))
                .revenueTrend(trend.stream().map(TrendBucket::toPoint).collect(java.util.stream.Collectors.toList()))
                .trendGranularity(granularity)
                .subscriptions(buildSubscriptions(to))
                .build();
    }

    /**
     * The card set of the Analytics tab: 7 ngay / 30 ngay / nam hien tai + cua so duoc yeu cau,
     * moi card kem % tang truong so voi ky lien truoc co cung do dai.
     */
    private AdminAnalyticsResponse.Revenue buildRevenue(LocalDateTime from, LocalDateTime to) {
        AdminAnalyticsResponse.RevenueCard weekly = revenueCard("7 ngày gần nhất",
                to.minusDays(7), to, to.minusDays(14), to.minusDays(7));
        AdminAnalyticsResponse.RevenueCard monthly = revenueCard("30 ngày gần nhất",
                to.minusDays(30), to, to.minusDays(60), to.minusDays(30));

        LocalDateTime yearStart = to.toLocalDate().withDayOfYear(1).atStartOfDay();
        AdminAnalyticsResponse.RevenueCard yearly = revenueCard("Năm hiện tại",
                yearStart, to, yearStart.minusYears(1), to.minusYears(1));

        return AdminAnalyticsResponse.Revenue.builder()
                .weekly(weekly)
                .monthly(monthly)
                .yearly(yearly)
                .period(revenueCard("Trong khoảng đang xem", from, to,
                        from.minus(Duration.between(from, to)), from))
                .build();
    }

    private AdminAnalyticsResponse.RevenueCard revenueCard(String label, LocalDateTime from, LocalDateTime to,
                                                           LocalDateTime prevFrom, LocalDateTime prevTo) {
        List<PaymentOrder> current = orderRepository.findByStatusAndPaidAtBetween(OrderStatus.PAID, from, to);
        List<PaymentOrder> previous = orderRepository.findByStatusAndPaidAtBetween(OrderStatus.PAID, prevFrom, prevTo);
        BigDecimal amount = sumAmount(current);
        BigDecimal prevAmount = sumAmount(previous);

        Double growth = null;
        if (prevAmount.compareTo(BigDecimal.ZERO) > 0) {
            growth = amount.subtract(prevAmount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(prevAmount, 2, RoundingMode.HALF_UP)
                    .doubleValue();
        }

        return AdminAnalyticsResponse.RevenueCard.builder()
                .label(label)
                .from(from)
                .to(to)
                .amount(amount)
                .paidOrders(current.size())
                .previousAmount(prevAmount)
                .growthPercent(growth)
                .build();
    }

    /** Muc 7/8 cua bang thong ke: dem goi dang hieu luc theo chu ky goi (>= 300 ngay = goi nam). */
    private AdminAnalyticsResponse.Subscriptions buildSubscriptions(LocalDateTime now) {
        List<ClubSubscription> active = subscriptionRepository.findByStatus(SubscriptionStatus.ACTIVE);

        long yearly = active.stream()
                .filter(s -> s.getSubscriptionPackage() != null
                        && s.getSubscriptionPackage().getDurationDays() != null
                        && s.getSubscriptionPackage().getDurationDays() >= 300)
                .count();
        long expiring = active.stream()
                .filter(s -> s.getEndDate() != null
                        && !s.getEndDate().isBefore(now)
                        && s.getEndDate().isBefore(now.plusDays(30)))
                .count();
        long payingUsers = active.stream()
                .map(s -> s.getOrder() != null ? s.getOrder().getUser() : null)
                .filter(Objects::nonNull)
                .map(User::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return AdminAnalyticsResponse.Subscriptions.builder()
                .activeMonthly(active.size() - yearly)
                .activeYearly(yearly)
                .totalActive(active.size())
                .expiringIn30Days(expiring)
                .payingUsers(payingUsers)
                .build();
    }

    /** Muc 13: dong tien theo tung ngay (cua so <= 62 ngay) hoac tung thang (cua so lon hon). */
    private List<TrendBucket> buildTrend(LocalDateTime from, LocalDateTime to) {
        List<PaymentOrder> paid = orderRepository.findByStatusAndPaidAtBetween(OrderStatus.PAID, from, to);
        boolean daily = Duration.between(from, to).toDays() <= 62;

        LinkedHashMap<String, TrendBucket> buckets = new LinkedHashMap<>();
        if (daily) {
            for (LocalDate d = from.toLocalDate(); !d.isAfter(to.toLocalDate()); d = d.plusDays(1)) {
                buckets.put(d.toString(), new TrendBucket(d.toString(), d.format(DAY_LABEL), false));
            }
        } else {
            for (YearMonth m = YearMonth.from(from);
                 !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
                buckets.put(m.toString(), new TrendBucket(m.toString(),
                        String.format("%02d/%d", m.getMonthValue(), m.getYear()), true));
            }
        }

        for (PaymentOrder order : paid) {
            LocalDateTime paidAt = order.getPaidAt() == null ? order.getCreatedAt() : order.getPaidAt();
            if (paidAt == null) {
                continue;
            }
            String key = daily ? paidAt.toLocalDate().toString() : YearMonth.from(paidAt).toString();
            TrendBucket bucket = buckets.get(key);
            if (bucket != null) {
                bucket.amount = bucket.amount.add(order.getAmount() == null ? BigDecimal.ZERO : order.getAmount());
                bucket.orders++;
            }
        }
        return new ArrayList<>(buckets.values());
    }

    private static boolean isDailyBucket(TrendBucket bucket) {
        return !bucket.monthly;
    }

    private static final class TrendBucket {
        private final String bucket;
        private final String label;
        private final boolean monthly;
        private BigDecimal amount = BigDecimal.ZERO;
        private long orders;

        private TrendBucket(String bucket, String label, boolean monthly) {
            this.bucket = bucket;
            this.label = label;
            this.monthly = monthly;
        }

        private AdminAnalyticsResponse.TrendPoint toPoint() {
            return AdminAnalyticsResponse.TrendPoint.builder()
                    .bucket(bucket)
                    .label(label)
                    .amount(amount)
                    .paidOrders(orders)
                    .build();
        }
    }

    private List<AdminAnalyticsResponse.FunnelStep> buildFunnel(Map<String, Set<String>> visitorsByStep,
                                                                long newUsers, long checkouts, long paidOrders) {
        List<AdminAnalyticsResponse.FunnelStep> steps = new ArrayList<>();
        steps.add(eventStep(PAGE_VIEW, "Lượt xem trang", "visitor", visitorsByStep));
        steps.add(eventStep(PRICING_VIEW, "Xem bảng giá gói dịch vụ", "visitor", visitorsByStep));
        steps.add(AdminAnalyticsResponse.FunnelStep.builder()
                .step(SIGNUP).label("Đăng ký tài khoản mới").unit("user").count(newUsers).build());
        steps.add(AdminAnalyticsResponse.FunnelStep.builder()
                .step(CHECKOUT_START).label("Bắt đầu thanh toán (tạo đơn)").unit("order").count(checkouts).build());
        steps.add(AdminAnalyticsResponse.FunnelStep.builder()
                .step(PAYMENT_SUCCESS).label("Thanh toán thành công").unit("order").count(paidOrders).build());

        long start = steps.get(0).getCount();
        Long previous = null;
        for (AdminAnalyticsResponse.FunnelStep step : steps) {
            long count = step.getCount();
            step.setConversionFromPrevious(previous == null || previous == 0 ? null : ratio(count, previous));
            step.setConversionFromStart(start == 0 ? null : ratio(count, start));
            previous = count;
        }
        return steps;
    }

    private AdminAnalyticsResponse.FunnelStep eventStep(String eventName, String label, String unit,
                                                        Map<String, Set<String>> visitorsByStep) {
        Set<String> visitors = visitorsByStep.get(eventName);
        return AdminAnalyticsResponse.FunnelStep.builder()
                .step(eventName)
                .label(label)
                .unit(unit)
                .count(visitors == null ? 0 : visitors.size())
                .build();
    }

    private List<AdminAnalyticsResponse.ChannelStat> buildChannels(
            Map<String, Set<String>> visitorsByChannel,
            Map<Integer, String> firstTouchByUser,
            List<PaymentOrder> createdOrders,
            List<PaymentOrder> paidOrders) {

        Map<String, AdminAnalyticsResponse.ChannelStat> byChannel = new LinkedHashMap<>();
        visitorsByChannel.forEach((key, visitors) -> byChannel.put(key, AdminAnalyticsResponse.ChannelStat.builder()
                .source(key.split("\\|", 2)[0])
                .medium(key.contains("|") ? key.split("\\|", 2)[1] : null)
                .visitors(visitors.size())
                .revenue(BigDecimal.ZERO)
                .build()));

        for (PaymentOrder order : createdOrders) {
            AdminAnalyticsResponse.ChannelStat stat = resolveChannel(byChannel, firstTouchByUser, order);
            if (stat != null) {
                stat.setCheckouts(stat.getCheckouts() + 1);
            }
        }
        for (PaymentOrder order : paidOrders) {
            AdminAnalyticsResponse.ChannelStat stat = resolveChannel(byChannel, firstTouchByUser, order);
            if (stat != null) {
                stat.setPaidOrders(stat.getPaidOrders() + 1);
                if (order.getAmount() != null) {
                    stat.setRevenue(stat.getRevenue().add(order.getAmount()));
                }
            }
        }

        return byChannel.values().stream()
                .sorted(Comparator.comparing(AdminAnalyticsResponse.ChannelStat::getVisitors).reversed())
                .limit(12)
                .collect(java.util.stream.Collectors.toList());
    }

    /** Don hang duoc quy ve kenh UTM ma tai khoản do lan dau tien (first-touch attribution). */
    private AdminAnalyticsResponse.ChannelStat resolveChannel(
            Map<String, AdminAnalyticsResponse.ChannelStat> byChannel,
            Map<Integer, String> firstTouchByUser,
            PaymentOrder order) {
        if (order.getUser() == null || order.getUser().getUserId() == null) {
            return null;
        }
        String key = firstTouchByUser.get(order.getUser().getUserId());
        return key == null ? null : byChannel.get(key);
    }

    private static String channelKey(String source, String medium) {
        return source.trim() + "|" + (medium == null || medium.isBlank() ? "" : medium.trim());
    }

    private static BigDecimal sumAmount(List<PaymentOrder> orders) {
        return orders.stream()
                .map(o -> o.getAmount() == null ? BigDecimal.ZERO : o.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Double ratio(long count, long base) {
        return Math.round((count * 10000.0) / base) / 100.0;
    }

    private static String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }
}
