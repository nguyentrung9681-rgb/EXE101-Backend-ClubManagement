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
            description = "Trả về totals, funnel, channels (utm_source/medium), revenue (7 ngày/30 ngày/năm + % tăng trưởng), revenueTrend và subscriptions")
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
