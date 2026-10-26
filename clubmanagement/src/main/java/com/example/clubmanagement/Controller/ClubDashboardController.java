package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.ClubDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
@RequiredArgsConstructor
public class ClubDashboardController {

    private final ClubDashboardService dashboardService;

    /**
     * Dashboard tổng quan CLB.
     * GET /api/clubs/{clubId}/dashboard?year=2026
     */
    @GetMapping("/{clubId}/dashboard")
    public ResponseEntity<?> getDashboard(
            @PathVariable Integer clubId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(dashboardService.getOverview(clubId, effectiveUserId, year));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
