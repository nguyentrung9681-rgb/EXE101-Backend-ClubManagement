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
import vn.payos.type.Webhook;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@Tag(name = "Payment & Subscription", description = "APIs quản lý gói dịch vụ và thanh toán qua VietQR (PayOS)")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
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

    @PostMapping("/payments/payos-webhook")
    @Operation(summary = "Webhook (IPN) nhận thông báo thanh toán tự động từ PayOS")
    public ResponseEntity<?> handlePayOSWebhook(@RequestBody Webhook webhookData) {
        try {
            return ResponseEntity.ok(paymentService.processWebhook(webhookData));
        } catch (Exception e) {
            return errorEntity(e);
        }
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
