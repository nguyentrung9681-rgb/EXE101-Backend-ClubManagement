package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.AnalyticsService;
import com.example.clubmanagement.dto.AnalyticsEventRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
@Tag(name = "Analytics", description = "Ghi nhận hành vi người dùng để đo phễu chuyển đổi và nguồn UTM")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    /**
     * Frontend goi khi nguoi dung den trang, xem bang gia... kem utm_source/medium/campaign.
     * userId luon lay tu JWT (neu co) de khong the gui event gan voi tai khoan khac.
     */
    @PostMapping("/events")
    @Operation(summary = "Ghi nhận một hành vi từ frontend",
            description = "eventName chi chu thuong/so/gach duoi; sessionId va UTM duoc luu de quy ke doanh thu")
    public ResponseEntity<?> track(@RequestBody AnalyticsEventRequest request) {
        try {
            analyticsService.track(request, SecurityUtils.getCurrentUserId());
            return ResponseEntity.ok(Map.of("status", "recorded"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}
