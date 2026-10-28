package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.PaymentService;
import com.example.clubmanagement.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@lombok.extern.slf4j.Slf4j
@Tag(name = "Payment & Subscription", description = "APIs quản lý gói dịch vụ và thanh toán qua VietQR (PayOS)")
public class PaymentController {

    private final PaymentService paymentService;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    public PaymentController(PaymentService paymentService, tools.jackson.databind.ObjectMapper objectMapper) {
        this.paymentService = paymentService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/packages")
    @Operation(summary = "Lấy danh sách các gói dịch vụ khả dụng")
    public ResponseEntity<List<SubscriptionPackageResponse>> getPackages() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofMinutes(10)).cachePublic())
                .body(paymentService.getAllPackages());
    }

    @PostMapping("/payments/create-checkout")
    @Operation(summary = "Tạo đơn hàng thanh toán gói dịch vụ (Sinh link/mã VietQR PayOS)")
    public ResponseEntity<?> createCheckout(@RequestBody CreatePaymentRequest request) {
        try {
            // userId trong body phải trùng với tài khoản đang đăng nhập
            request.setUserId(SecurityUtils.resolveUserId(request.getUserId()));
            PaymentOrderResponse response = paymentService.createCheckoutOrder(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return errorEntity(e);
        }
    }

    @GetMapping("/payments/check-permission")
    @Operation(summary = "Kiểm tra người dùng hiện tại có quyền thanh toán cho CLB (Chủ nhiệm CLB) hay không")
    public ResponseEntity<?> checkPaymentPermission(@RequestParam Integer clubId,
                                                   @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            boolean canPay = paymentService.canUserPayForClub(clubId, effectiveUserId);
            return ResponseEntity.ok(Map.of("canPay", canPay, "clubId", clubId, "userId", effectiveUserId));
        } catch (Exception e) {
            return errorEntity(e);
        }
    }

    @PostMapping(value = "/payments/payos-webhook", consumes = org.springframework.http.MediaType.ALL_VALUE)
    @Operation(summary = "Webhook (IPN) nhận thông báo thanh toán tự động từ PayOS")
    public ResponseEntity<?> handlePayOSWebhook(@RequestBody(required = false) String rawBody) {
        try {
            log.info("PayOS goi webhook (len={}): {}",
                    rawBody == null ? 0 : rawBody.length(),
                    preview(rawBody));

            vn.payos.type.Webhook webhook = toWebhook(rawBody);
            if (webhook == null) {
                // PayOS chi Ping kiem tra URL truoc khi cho phep luu Webhook Url.
                // Payload nay khong co noi dung don hang nen khong xuyen qua duoc viec kiem chu ky.
                return ResponseEntity.ok(webhookAck());
            }
            PaymentOrderResponse processed = paymentService.processWebhook(webhook);
            return ResponseEntity.ok(processed == null ? webhookAck() : processed);
        } catch (Exception e) {
            return errorEntity(e);
        }
    }

    private static Map<String, String> webhookAck() {
        return Map.of("code", "00", "message", "S-Club webhook endpoint is ready");
    }

    private vn.payos.type.Webhook toWebhook(String rawBody) {
        if (rawBody == null) {
            return null;
        }
        int start = rawBody.indexOf('{');
        if (start < 0) {
            return null;
        }
        try {
            return objectMapper.readValue(rawBody.substring(start), PayOSWebhookRequest.class).toWebhook();
        } catch (Exception e) {
            log.warn("Khong doc duoc JSON webhook cua PayOS: {}", e.getMessage());
            return null;
        }
    }

    private static String preview(String rawBody) {
        if (rawBody == null) {
            return "null";
        }
        return rawBody.length() <= 600 ? rawBody : rawBody.substring(0, 600) + "...";
    }

    @GetMapping("/payments/order/{orderCode}")
    @Operation(summary = "Lấy thông tin và trạng thái đơn hàng theo orderCode (Dùng cho FE Polling)")
    public ResponseEntity<?> getOrderDetails(@PathVariable Long orderCode) {
        try {
            return ResponseEntity.ok(paymentService.getOrderByCode(orderCode, SecurityUtils.getCurrentUserId()));
        } catch (Exception e) {
            return errorEntity(e);
        }
    }

    @PostMapping("/payments/order/{orderCode}/confirm-test")
    @Operation(summary = "Xác nhận thanh toán thủ công - CHỈ dùng khi test, phải bật PAYOS_MANUAL_CONFIRM_ENABLED",
               description = "Mặc định bị vô hiệu hoá trên mọi môi trường. Khi bật, chỉ chủ đơn hàng mới gọi được.")
    public ResponseEntity<?> confirmOrderManual(@PathVariable Long orderCode) {
        try {
            return ResponseEntity.ok(paymentService.confirmOrderManual(orderCode, SecurityUtils.getCurrentUserId()));
        } catch (Exception e) {
            return errorEntity(e);
        }
    }

    @PostMapping("/payments/order/{orderCode}/cancel")
    @Operation(summary = "Hủy đơn hàng thanh toán (chỉ chủ đơn hoặc Chủ nhiệm CLB của đơn)")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderCode) {
        try {
            return ResponseEntity.ok(paymentService.cancelOrder(orderCode, SecurityUtils.getCurrentUserId()));
        } catch (Exception e) {
            return errorEntity(e);
        }
    }

    @GetMapping("/payments/club/{clubId}/subscription")
    @Operation(summary = "Lấy gói dịch vụ đang hoạt động của Câu Lạc Bộ")
    public ResponseEntity<ClubSubscriptionResponse> getClubSubscription(@PathVariable Integer clubId) {
        ClubSubscriptionResponse sub = paymentService.getActiveSubscriptionForClub(clubId);
        if (sub == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(sub);
    }

    private ResponseEntity<Map<String, String>> errorEntity(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : "Yêu cầu không hợp lệ";
        HttpStatus status;
        if (e instanceof SecurityException) {
            status = HttpStatus.FORBIDDEN;
        } else if (e instanceof IllegalArgumentException) {
            status = HttpStatus.BAD_REQUEST;
        } else if (e instanceof IllegalStateException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status).body(Map.of("message", message));
    }
}
