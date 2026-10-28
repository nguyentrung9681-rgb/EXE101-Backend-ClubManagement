package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.*;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.type.ItemData;
import vn.payos.type.PaymentData;
import vn.payos.type.CheckoutResponseData;
import vn.payos.type.PaymentLinkData;
import vn.payos.type.PayOSResponse;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;
import vn.payos.util.SignatureUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private final SubscriptionPackageRepository packageRepository;
    private final PaymentOrderRepository orderRepository;
    private final ClubSubscriptionRepository subscriptionRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final EmailService emailService;
    private final AnalyticsService analyticsService;
    private final PayOS payOS;

    @Value("${PAYOS_CLIENT_ID:${payos.client-id:}}")
    private String clientId;

    @Value("${PAYOS_API_KEY:${payos.api-key:}}")
    private String apiKey;

    @Value("${PAYOS_CHECKSUM_KEY:${payos.checksum-key:}}")
    private String checksumKey;

    @Value("${payos.return-url:https://exe-ebon.vercel.app/payment/success}")
    private String returnUrl;

    @Value("${payos.cancel-url:https://exe-ebon.vercel.app/payment/cancel}")
    private String cancelUrl;

    /** Endpoint xac nhan thu cong chi duoc mo khi test o local/sandbox. */
    @Value("${payos.manual-confirm.enabled:false}")
    private boolean manualConfirmEnabled;

    /** Cho phep tao don hang voi QR gia khi chua co key PayOS (chi dung khi dev). */
    @Value("${payos.allow-unconfigured-checkout:false}")
    private boolean allowUnconfiguredCheckout;

    public PaymentService(
            SubscriptionPackageRepository packageRepository,
            PaymentOrderRepository orderRepository,
            ClubSubscriptionRepository subscriptionRepository,
            ClubRepository clubRepository,
            UserRepository userRepository,
            ClubMemberRepository clubMemberRepository,
            EmailService emailService,
            AnalyticsService analyticsService,
            PayOS payOS) {
        this.packageRepository = packageRepository;
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.emailService = emailService;
        this.analyticsService = analyticsService;
        this.payOS = payOS;
    }

    public List<SubscriptionPackageResponse> getAllPackages() {
        return packageRepository.findByIsActiveTrue().stream()
                .map(this::mapPackageToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public PaymentOrderResponse createCheckoutOrder(CreatePaymentRequest request) {
        if (request.getClubId() == null) {
            throw new IllegalArgumentException("Vui lòng cung cấp mã Câu lạc bộ (clubId) để thanh toán gói dịch vụ.");
        }

        if (request.getUserId() == null) {
            throw new IllegalArgumentException("Vui lòng cung cấp mã Người dùng (userId) thực hiện thanh toán.");
        }

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new IllegalArgumentException("Câu lạc bộ không tồn tại ID: " + request.getClubId()));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Người dùng không tồn tại ID: " + request.getUserId()));

        // Phân quyền: Chỉ thành viên ACTIVE có vai trò PRESIDENT (Chủ nhiệm) của CLB này mới được phép thanh toán gói
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserIdAndStatus(
                request.getClubId(), request.getUserId(), ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException("Người dùng không phải là thành viên đang hoạt động của câu lạc bộ này!"));

        if (member.getRole() != ClubMemberRole.PRESIDENT) {
            throw new SecurityException("Chỉ Chủ nhiệm câu lạc bộ (PRESIDENT) mới có quyền thực hiện thanh toán gói dịch vụ!");
        }

        SubscriptionPackage pack = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new IllegalArgumentException("Gói dịch vụ không tồn tại ID: " + request.getPackageId()));

        requirePayOsConfigured();

        long orderCode = generateOrderCode();

        ItemData item = ItemData.builder()
                .name(pack.getName())
                .quantity(1)
                .price(pack.getPrice().intValue())
                .build();

        String description = "Thanh toan " + pack.getCode();
        if (description.length() > 25) {
            description = description.substring(0, 25);
        }

        PaymentData paymentData = PaymentData.builder()
                .orderCode(orderCode)
                .amount(pack.getPrice().intValue())
                .description(description)
                .items(List.of(item))
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .build();

        String checkoutUrl = null;
        String qrCodeUrl = null;

        try {
            CheckoutResponseData data = createPayOSPaymentLink(paymentData);
            checkoutUrl = data.getCheckoutUrl();
            qrCodeUrl = data.getQrCode();
        } catch (Exception e) {
            log.error("Lỗi gọi PayOS API khi tạo link thanh toán cho đơn {}: {}", orderCode, e.getMessage());
            if (!allowUnconfiguredCheckout) {
                throw new IllegalStateException(
                        "Không tạo được link thanh toán PayOS. Vui lòng thử lại sau hoặc liên hệ hỗ trợ.");
            }
            // Chi dung khi dev local: PayOS that khong the thanh toan duoc.
            checkoutUrl = returnUrl + "?orderCode=" + orderCode;
            qrCodeUrl = "https://img.vietqr.io/image/MB-0987654321-compact2.png?amount="
                    + pack.getPrice().longValue()
                    + "&addInfo=PAY" + orderCode
                    + "&accountName=EXE101%20CLUB%20MANAGEMENT";
        }

        PaymentOrder order = PaymentOrder.builder()
                .orderCode(orderCode)
                .user(user)
                .club(club)
                .subscriptionPackage(pack)
                .amount(pack.getPrice())
                .status(OrderStatus.PENDING)
                .checkoutUrl(checkoutUrl)
                .qrCodeUrl(qrCodeUrl)
                .paymentMethod("VIETQR")
                .build();

        PaymentOrder saved = orderRepository.save(order);

        analyticsService.recordQuiet(AnalyticsService.CHECKOUT_START, user.getUserId(), club.getId(),
                saved.getOrderCode(), saved.getAmount(), null, null, null);

        return mapOrderToResponse(saved);
    }

    @Transactional
    public PaymentOrderResponse processWebhook(Webhook webhook) {
        if (webhook == null || webhook.getData() == null) {
            throw new IllegalArgumentException("Dữ liệu Webhook không hợp lệ!");
        }

        WebhookData webhookData;
        try {
            webhookData = payOS.verifyPaymentWebhookData(webhook);
        } catch (Exception e) {
            log.warn("Từ chối webhook PayOS: checksum không hợp lệ cho đơn {}", webhook.getData().getOrderCode());
            throw new SecurityException("Chữ ký webhook PayOS không hợp lệ - yêu cầu bị từ chối.");
        }

        if (webhookData == null || webhookData.getOrderCode() == null) {
            throw new IllegalArgumentException("Dữ liệu Webhook không hợp lệ!");
        }

        Long orderCode = webhookData.getOrderCode();
        PaymentOrder order = orderRepository.findByOrderCode(orderCode).orElse(null);
        if (order == null) {
            // PayOS dung chinh mau IPN trong tai lieu (don 123) de kiem tra Webhook Url.
            // Chu ky da hop le o tren, nen ACK 200 de PayOS ghi nhan endpoint hoat dong.
            log.warn("Webhook PayOS co chu ky hop le nhung khong tim thay don {} - bo qua", orderCode);
            return null;
        }

        // Kiểm tra Idempotency - Tránh xử lý trùng lặp
        if (order.getStatus() == OrderStatus.PAID) {
            return mapOrderToResponse(order);
        }

        if ("00".equals(webhookData.getCode())) { // 00 là mã thành công từ PayOS
            verifyPaidAmount(order, webhookData);
            markOrderPaid(order, webhookData.getReference());
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        return mapOrderToResponse(order);
    }

    /**
     * Webhook da duoc kiem chu ky o tren; loi nay chac chan don thanh toan that
     * (khong phai payload gia mao ghi de don dang cho).
     */
    private void verifyPaidAmount(PaymentOrder order, WebhookData data) {
        if (data.getAmount() == null) {
            throw new SecurityException("Webhook thiếu số tiền giao dịch, không xác nhận đơn " + order.getOrderCode());
        }
        int expected = order.getAmount().intValue();
        if (data.getAmount() != expected) {
            log.error("Từ chối webhook: số tiền {} không khớp đơn {} (kỳ vọng {})",
                    data.getAmount(), order.getOrderCode(), expected);
            throw new SecurityException("Số tiền trong webhook không khớp đơn hàng.");
        }
        if (data.getCurrency() != null && !"VND".equalsIgnoreCase(data.getCurrency())) {
            throw new SecurityException("Đơn vị tiền tệ trong webhook không hợp lệ.");
        }
    }

    /** Duong dan duy nhat chuyen mot don sang PAID: luu tran, kich hoat goi, gui email xac nhan. */
    private void markOrderPaid(PaymentOrder order, String reference) {
        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setTransactionNo(reference != null && !reference.isBlank()
                ? reference
                : "PAYOS_" + order.getOrderCode());
        orderRepository.save(order);

        ClubSubscription subscription = order.getClub() != null ? activateClubSubscription(order) : null;
        sendOrderConfirmation(order, subscription);

        Integer buyerId = order.getUser() != null ? order.getUser().getUserId() : null;
        Integer paidClubId = order.getClub() != null ? order.getClub().getId() : null;
        analyticsService.recordQuiet(AnalyticsService.PAYMENT_SUCCESS, buyerId, paidClubId,
                order.getOrderCode(), order.getAmount(), null, null, null);
    }

    /** Chi chinh chu don hang hoac Chu nhiem CLB cua don duoc phep thao tac tren don. */
    private void requireOrderAccess(PaymentOrder order, Integer currentUserId) {
        if (currentUserId == null) {
            throw new SecurityException("Yêu cầu không được xác thực! Vui lòng cung cấp JWT Token hợp lệ.");
        }
        if (order.getUser() != null && currentUserId.equals(order.getUser().getUserId())) {
            return;
        }
        if (order.getClub() != null && clubMemberRepository.findByClubIdAndUserUserIdAndStatus(
                        order.getClub().getId(), currentUserId, ClubMemberStatus.ACTIVE)
                .map(m -> m.getRole() == ClubMemberRole.PRESIDENT)
                .orElse(false)) {
            return;
        }
        throw new SecurityException("Bạn không có quyền thao tác trên đơn hàng này!");
    }

    @Transactional
    public PaymentOrderResponse confirmOrderManual(Long orderCode, Integer currentUserId) {
        if (!manualConfirmEnabled) {
            throw new SecurityException("Xác nhận thủ công đã bị vô hiệu hoá ở môi trường này. "
                    + "Đơn hàng chỉ được xác nhận qua webhook PayOS.");
        }

        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã: " + orderCode));
        requireOrderAccess(order, currentUserId);

        if (order.getStatus() == OrderStatus.PAID) {
            return mapOrderToResponse(order);
        }

        markOrderPaid(order, "MANUAL_" + System.currentTimeMillis());
        return mapOrderToResponse(order);
    }

    @Transactional
    public PaymentOrderResponse cancelOrder(Long orderCode, Integer currentUserId) {
        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã: " + orderCode));
        requireOrderAccess(order, currentUserId);

        if (order.getStatus() == OrderStatus.PENDING) {
            try {
                payOS.cancelPaymentLink(orderCode, "Người dùng hủy giao dịch");
            } catch (Exception e) {
                log.warn("Không hủy được link thanh toán PayOS cho đơn {}: {}", orderCode, e.getMessage());
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        return mapOrderToResponse(order);
    }

    @Transactional
    public PaymentOrderResponse getOrderByCode(Long orderCode, Integer currentUserId) {
        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng mã: " + orderCode));
        requireOrderAccess(order, currentUserId);
        if (order.getStatus() == OrderStatus.PENDING) {
            syncOrderWithPayOS(order);
        }
        return mapOrderToResponse(order);
    }

    public boolean canUserPayForClub(Integer clubId, Integer userId) {
        if (clubId == null || userId == null) return false;
        return clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                .map(m -> m.getRole() == ClubMemberRole.PRESIDENT)
                .orElse(false);
    }

    @Transactional
    public ClubSubscriptionResponse getActiveSubscriptionForClub(Integer clubId) {
        ClubSubscription sub = subscriptionRepository.findFirstByClubIdAndStatusOrderByEndDateDesc(clubId, SubscriptionStatus.ACTIVE)
                .orElse(null);
        if (sub == null) {
            // Webhook PayOS đã là nguồn sự thật; ở đây chỉ dò lại vài đơn vừa tạo
            // để thu dọn phiên checkout mà người dùng vừa rời trang.
            List<PaymentOrder> pendingOrders = orderRepository
                    .findTop3ByClubIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                            clubId, OrderStatus.PENDING, LocalDateTime.now().minusMinutes(30));
            boolean updatedAny = false;
            for (PaymentOrder pending : pendingOrders) {
                if (syncOrderWithPayOS(pending)) {
                    updatedAny = true;
                }
            }
            if (updatedAny) {
                sub = subscriptionRepository.findFirstByClubIdAndStatusOrderByEndDateDesc(clubId, SubscriptionStatus.ACTIVE)
                        .orElse(null);
            }
        }
        if (sub == null) return null;
        return mapSubscriptionToResponse(sub);
    }

    @Transactional
    public boolean syncOrderWithPayOS(PaymentOrder order) {
        if (order == null || order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        try {
            PaymentLinkData paymentLinkData = payOS.getPaymentLinkInformation(order.getOrderCode());
            if (paymentLinkData != null) {
                String statusStr = paymentLinkData.getStatus();
                if ("PAID".equalsIgnoreCase(statusStr)) {
                    String reference = paymentLinkData.getTransactions() != null
                            && !paymentLinkData.getTransactions().isEmpty()
                            ? paymentLinkData.getTransactions().get(0).getReference()
                            : null;
                    markOrderPaid(order, reference);
                    return true;
                } else if ("CANCELLED".equalsIgnoreCase(statusStr)) {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancelledAt(LocalDateTime.now());
                    orderRepository.save(order);
                }
            }
        } catch (Exception e) {
            log.warn("Không thể tự động đồng bộ đơn hàng {} từ PayOS API: {}",
                    order.getOrderCode(), e.getMessage());
        }
        return false;
    }

    private ClubSubscription activateClubSubscription(PaymentOrder order) {
        Club club = order.getClub();
        SubscriptionPackage pack = order.getSubscriptionPackage();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startDate = now;

        // Kiểm tra xem CLB có gói đang active không
        ClubSubscription existing = subscriptionRepository
                .findFirstByClubIdAndStatusOrderByEndDateDesc(club.getId(), SubscriptionStatus.ACTIVE)
                .orElse(null);

        if (existing != null && existing.getEndDate().isAfter(now)) {
            // Nối tiếp thời hạn từ ngày kết thúc của gói cũ
            startDate = existing.getEndDate();
        }

        LocalDateTime endDate = startDate.plusDays(pack.getDurationDays());

        ClubSubscription subscription = ClubSubscription.builder()
                .club(club)
                .subscriptionPackage(pack)
                .order(order)
                .startDate(startDate)
                .endDate(endDate)
                .status(SubscriptionStatus.ACTIVE)
                .build();

        subscriptionRepository.save(subscription);
        return subscription;
    }

    /**
     * Gui hoa don xac nhan den khach hang. Email duoc gui bat dong bo va loi gui
     * khong duoc lam hong giao dich thanh toan.
     */
    private void sendOrderConfirmation(PaymentOrder order, ClubSubscription subscription) {
        User buyer = order.getUser();
        if (buyer == null || buyer.getEmail() == null || buyer.getEmail().isBlank()) {
            log.warn("Bỏ qua email xác nhận đơn {}: người mua không có email", order.getOrderCode());
            return;
        }
        try {
            emailService.sendOrderConfirmationEmail(
                    buyer.getEmail(),
                    buyer.getFullName(),
                    order.getOrderCode(),
                    order.getSubscriptionPackage().getName(),
                    order.getAmount(),
                    order.getClub() != null ? order.getClub().getName() : null,
                    order.getPaymentMethod(),
                    order.getTransactionNo(),
                    order.getPaidAt(),
                    subscription != null ? subscription.getEndDate() : null);
        } catch (Exception e) {
            log.error("Không gửi được email xác nhận đơn {}: {}", order.getOrderCode(), e.getMessage());
        }
    }

    private void requirePayOsConfigured() {
        if (isPayOsConfigured() || allowUnconfiguredCheckout) {
            return;
        }
        log.error("Không thể tạo link thanh toán: thiếu PAYOS_CLIENT_ID / PAYOS_API_KEY / PAYOS_CHECKSUM_KEY");
        throw new IllegalStateException("Hệ thống thanh toán chưa được kích hoạt, vui lòng liên hệ hỗ trợ.");
    }

    private boolean isPayOsConfigured() {
        return clientId != null && !clientId.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && checksumKey != null && !checksumKey.isBlank();
    }

    /** Ma don dua tren timestamp nen hai don tao cung luc co the trung nhau. */
    private long generateOrderCode() {
        for (int attempt = 0; attempt < 5; attempt++) {
            long candidate = System.currentTimeMillis() % 1_000_000_000L
                    + ThreadLocalRandom.current().nextInt(1000);
            if (candidate > 0 && !orderRepository.existsByOrderCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Không sinh được mã đơn hàng, vui lòng thử lại");
    }

    private SubscriptionPackageResponse mapPackageToResponse(SubscriptionPackage pack) {
        return SubscriptionPackageResponse.builder()
                .id(pack.getId())
                .code(pack.getCode())
                .name(pack.getName())
                .price(pack.getPrice())
                .durationDays(pack.getDurationDays())
                .description(pack.getDescription())
                .features(pack.getFeatures())
                .build();
    }

    private PaymentOrderResponse mapOrderToResponse(PaymentOrder order) {
        return PaymentOrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .userId(order.getUser() != null ? order.getUser().getUserId() : null)
                .clubId(order.getClub() != null ? order.getClub().getId() : null)
                .packageId(order.getSubscriptionPackage().getId())
                .packageName(order.getSubscriptionPackage().getName())
                .amount(order.getAmount())
                .status(order.getStatus())
                .checkoutUrl(order.getCheckoutUrl())
                .qrCodeUrl(order.getQrCodeUrl())
                .paymentMethod(order.getPaymentMethod())
                .transactionNo(order.getTransactionNo())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .build();
    }

    private ClubSubscriptionResponse mapSubscriptionToResponse(ClubSubscription sub) {
        return ClubSubscriptionResponse.builder()
                .id(sub.getId())
                .clubId(sub.getClub().getId())
                .clubName(sub.getClub().getName())
                .packageId(sub.getSubscriptionPackage().getId())
                .packageName(sub.getSubscriptionPackage().getName())
                .startDate(sub.getStartDate())
                .endDate(sub.getEndDate())
                .status(sub.getStatus())
                .build();
    }

    private CheckoutResponseData createPayOSPaymentLink(PaymentData paymentData) throws Exception {
        return callPayOSApiDirectly(paymentData);
    }

    private CheckoutResponseData callPayOSApiDirectly(PaymentData paymentData) throws Exception {
        String signature = SignatureUtils.createSignatureOfPaymentRequest(paymentData, checksumKey);

        PaymentData dataWithSig = PaymentData.builder()
                .orderCode(paymentData.getOrderCode())
                .amount(paymentData.getAmount())
                .description(paymentData.getDescription())
                .items(paymentData.getItems())
                .returnUrl(paymentData.getReturnUrl())
                .cancelUrl(paymentData.getCancelUrl())
                .signature(signature)
                .build();

        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String jsonBody = mapper.writeValueAsString(dataWithSig);

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api-merchant.payos.vn/v2/payment-requests"))
                .header("Content-Type", "application/json")
                .header("x-client-id", clientId)
                .header("x-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        PayOSResponse payOSResponse = mapper.readValue(response.body(), PayOSResponse.class);
        if (!"00".equals(payOSResponse.getCode())) {
            throw new RuntimeException("PayOS API error [" + payOSResponse.getCode() + "]: " + payOSResponse.getDesc());
        }

        String dataJson = mapper.writeValueAsString(payOSResponse.getData());
        return mapper.readValue(dataJson, CheckoutResponseData.class);
    }
}
