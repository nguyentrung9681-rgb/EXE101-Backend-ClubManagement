package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.AnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/analytics")
@CrossOrigin(origins = "*")
@Tag(name = "Admin Analytics", description = "Phễu chuyển đổi và phân bổ kênh UTM cho trang quản trị")
public class AdminAnalyticsController {

    private final AnalyticsService analyticsService;

    public AdminAnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * GET /api/admin/analytics?days=30
     * Buoc page_view/pricing_view lay tu event frontend gui len;
     * signup/checkout/payment_success lay truc tiep tu du lieu that trong he thong.
     */
    @GetMapping
    @Operation(summary = "Lấy phễu chuyển đổi, kênh UTM và KPI doanh thu",
            description = "Trả về totals (kèm totalUsers, payingCustomers, averageOrderValue), funnel, "
                    + "channels (utm_source/medium + newUsers, conversionRate, averageOrderValue), "
                    + "revenue (7 ngày/30 ngày/năm + % tăng trưởng), revenueTrend, subscriptions, "
                    + "subscriptionSales (đơn bán gói theo kỳ), reviews (verified, coverage, tỉ lệ mua lại) "
                    + "và transactionGoal (mục tiêu 20 đơn; đếm lũy kế không phụ thuộc days: PAID, có paidAt, "
                    + "số tiền > 0, mã tham chiếu không rỗng và không bắt đầu bằng MANUAL_). "
                    + "progressPercent có thể vượt 100%; đây không phải kết quả đối chiếu sao kê.")
    public ResponseEntity<?> getAnalytics(
            @Parameter(description = "Số ngày gần nhất cần thống kê (1 - 365)")
            @RequestParam(defaultValue = "30") int days) {
        try {
            return ResponseEntity.ok(analyticsService.getAnalytics(days));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
