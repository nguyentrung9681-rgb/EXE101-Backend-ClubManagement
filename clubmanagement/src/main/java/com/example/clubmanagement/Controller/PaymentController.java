package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.PaymentService;
import com.example.clubmanagement.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.payos.type.Webhook;

import java.util.List;

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
        return ResponseEntity.ok(paymentService.getAllPackages());
    }

    @PostMapping("/payments/create-checkout")
    @Operation(summary = "Tạo đơn hàng thanh toán gói dịch vụ (Sinh link/mã VietQR PayOS)")
    public ResponseEntity<?> createCheckout(@RequestBody CreatePaymentRequest request) {
        try {
            PaymentOrderResponse response = paymentService.createCheckoutOrder(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/payments/check-permission")
    @Operation(summary = "Kiểm tra người dùng có quyền thanh toán cho CLB (Chủ nhiệm CLB) hay không")
    public ResponseEntity<?> checkPaymentPermission(@RequestParam Integer clubId, @RequestParam Integer userId) {
        boolean canPay = paymentService.canUserPayForClub(clubId, userId);
        return ResponseEntity.ok(java.util.Map.of("canPay", canPay, "clubId", clubId, "userId", userId));
    }

    @PostMapping("/payments/payos-webhook")
    @Operation(summary = "Webhook (IPN) nhận thông báo thanh toán tự động từ PayOS")
    public ResponseEntity<PaymentOrderResponse> handlePayOSWebhook(@RequestBody Webhook webhookData) {
        PaymentOrderResponse response = paymentService.processWebhook(webhookData);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/payments/order/{orderCode}")
    @Operation(summary = "Lấy thông tin và trạng thái đơn hàng theo orderCode (Dùng cho FE Polling)")
    public ResponseEntity<PaymentOrderResponse> getOrderDetails(@PathVariable Long orderCode) {
        return ResponseEntity.ok(paymentService.getOrderByCode(orderCode));
    }

    @PostMapping("/payments/order/{orderCode}/confirm-test")
    @Operation(summary = "Xác nhận thanh toán thủ công (Dành cho việc Test/Môi trường Dev)")
    public ResponseEntity<PaymentOrderResponse> confirmOrderManual(@PathVariable Long orderCode) {
        return ResponseEntity.ok(paymentService.confirmOrderManual(orderCode));
    }

    @PostMapping("/payments/order/{orderCode}/cancel")
    @Operation(summary = "Hủy đơn hàng thanh toán")
    public ResponseEntity<PaymentOrderResponse> cancelOrder(@PathVariable Long orderCode) {
        return ResponseEntity.ok(paymentService.cancelOrder(orderCode));
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
}
