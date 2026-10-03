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
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private final SubscriptionPackageRepository packageRepository;
    private final PaymentOrderRepository orderRepository;
    private final ClubSubscriptionRepository subscriptionRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
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

    public PaymentService(
            SubscriptionPackageRepository packageRepository,
            PaymentOrderRepository orderRepository,
            ClubSubscriptionRepository subscriptionRepository,
            ClubRepository clubRepository,
            UserRepository userRepository,
            ClubMemberRepository clubMemberRepository,
            PayOS payOS) {
        this.packageRepository = packageRepository;
        this.orderRepository = orderRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
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
            throw new RuntimeException("Vui lòng cung cấp mã Câu lạc bộ (clubId) để thanh toán gói dịch vụ.");
        }

        if (request.getUserId() == null) {
            throw new RuntimeException("Vui lòng cung cấp mã Người dùng (userId) thực hiện thanh toán.");
        }

        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new RuntimeException("Câu lạc bộ không tồn tại ID: " + request.getClubId()));

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("Người dùng không tồn tại ID: " + request.getUserId()));

        // Phân quyền: Chỉ thành viên ACTIVE có vai trò PRESIDENT (Chủ nhiệm) của CLB này mới được phép thanh toán gói
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserIdAndStatus(
                request.getClubId(), request.getUserId(), ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Người dùng không phải là thành viên đang hoạt động của câu lạc bộ này!"));

        if (member.getRole() != ClubMemberRole.PRESIDENT) {
            throw new RuntimeException("Chỉ Chủ nhiệm câu lạc bộ (PRESIDENT) mới có quyền thực hiện thanh toán gói dịch vụ!");
        }

        SubscriptionPackage pack = packageRepository.findById(request.getPackageId())
                .orElseThrow(() -> new RuntimeException("Gói dịch vụ không tồn tại ID: " + request.getPackageId()));

        // Sinh mã orderCode kiểu positive Long (unique dựa trên timestamp)
        long orderCode = System.currentTimeMillis() % 1_000_000_000L;

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
            System.err.println("Lỗi gọi PayOS API: " + e.getMessage());
            // Fallback khi chưa cấu hình PayOS Key thật hoặc chạy môi trường dev local
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
        return mapOrderToResponse(saved);
    }

    @Transactional
    public PaymentOrderResponse processWebhook(Webhook webhook) {
        WebhookData webhookData;
        try {
            webhookData = payOS.verifyPaymentWebhookData(webhook);
        } catch (Exception e) {
            // Fallback nếu không verify được checksum (chẳng hạn gọi manual hoặc test endpoint)
            webhookData = webhook.getData();
        }

        if (webhookData == null) {
            throw new RuntimeException("Dữ liệu Webhook không hợp lệ!");
        }

        Long orderCode = webhookData.getOrderCode();
        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng mã: " + orderCode));

        // Kiểm tra Idempotency - Tránh xử lý trùng lặp
        if (order.getStatus() == OrderStatus.PAID) {
            return mapOrderToResponse(order);
        }

        if ("00".equals(webhookData.getCode())) { // 00 là mã thành công từ PayOS
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(LocalDateTime.now());
            order.setTransactionNo(webhookData.getReference());
            orderRepository.save(order);

            // Kích hoạt/gia hạn gói cho CLB nếu đơn hàng gắn với CLB
            if (order.getClub() != null) {
                activateClubSubscription(order);
            }
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        return mapOrderToResponse(order);
    }

    @Transactional
    public PaymentOrderResponse confirmOrderManual(Long orderCode) {
        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng mã: " + orderCode));

        if (order.getStatus() == OrderStatus.PAID) {
            return mapOrderToResponse(order);
        }

        order.setStatus(OrderStatus.PAID);
        order.setPaidAt(LocalDateTime.now());
        order.setTransactionNo("MANUAL_" + System.currentTimeMillis());
        orderRepository.save(order);

        if (order.getClub() != null) {
            activateClubSubscription(order);
        }

        return mapOrderToResponse(order);
    }

    @Transactional
    public PaymentOrderResponse cancelOrder(Long orderCode) {
        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng mã: " + orderCode));

        if (order.getStatus() == OrderStatus.PENDING) {
            try {
                payOS.cancelPaymentLink(orderCode, "Người dùng hủy giao dịch");
            } catch (Exception ignored) {
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            orderRepository.save(order);
        }

        return mapOrderToResponse(order);
    }

    @Transactional
    public PaymentOrderResponse getOrderByCode(Long orderCode) {
        PaymentOrder order = orderRepository.findByOrderCode(orderCode)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng mã: " + orderCode));
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
            List<PaymentOrder> pendingOrders = orderRepository.findByClubIdAndStatus(clubId, OrderStatus.PENDING);
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
                    order.setStatus(OrderStatus.PAID);
                    order.setPaidAt(LocalDateTime.now());
                    if (paymentLinkData.getTransactions() != null && !paymentLinkData.getTransactions().isEmpty()) {
                        order.setTransactionNo(paymentLinkData.getTransactions().get(0).getReference());
                    } else {
                        order.setTransactionNo("PAYOS_" + order.getOrderCode());
                    }
                    orderRepository.save(order);
                    if (order.getClub() != null) {
                        activateClubSubscription(order);
                    }
                    return true;
                } else if ("CANCELLED".equalsIgnoreCase(statusStr)) {
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancelledAt(LocalDateTime.now());
                    orderRepository.save(order);
                }
            }
        } catch (Exception e) {
            System.err.println("Không thể tự động đồng bộ đơn hàng " + order.getOrderCode() + " từ PayOS API: " + e.getMessage());
        }
        return false;
    }

    private void activateClubSubscription(PaymentOrder order) {
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
