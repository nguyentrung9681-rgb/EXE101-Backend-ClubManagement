package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.FundCampaignService;
import com.example.clubmanagement.dto.FinanceDtos.CreateCampaignRequest;
import com.example.clubmanagement.dto.FinanceDtos.FundCampaignResponse;
import com.example.clubmanagement.dto.FinanceDtos.FundContributionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/fund-campaigns")
@RequiredArgsConstructor
@Tag(name = "Fund Campaign Management")
public class FundCampaignController {

    private final FundCampaignService fundCampaignService;

    @PostMapping
    @Operation(summary = "Thủ quỹ tạo đợt thu quỹ mới và tự động gửi thông báo")
    public ResponseEntity<FundCampaignResponse> createCampaign(
            @PathVariable Integer clubId,
            @RequestBody CreateCampaignRequest req) {
        Integer userId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(fundCampaignService.createCampaign(clubId, userId, req));
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách các đợt thu quỹ của CLB")
    public ResponseEntity<List<FundCampaignResponse>> getCampaigns(
            @PathVariable Integer clubId) {
        return ResponseEntity.ok(fundCampaignService.getCampaignsByClub(clubId));
    }

    @GetMapping("/{campaignId}")
    @Operation(summary = "Lấy thông tin chi tiết một đợt thu quỹ")
    public ResponseEntity<FundCampaignResponse> getCampaignById(
            @PathVariable Integer clubId,
            @PathVariable Integer campaignId) {
        return ResponseEntity.ok(fundCampaignService.getCampaignById(campaignId));
    }

    @GetMapping("/{campaignId}/contributions")
    @Operation(summary = "Lấy danh sách thành viên và trạng thái đóng tiền của đợt thu quỹ")
    public ResponseEntity<List<FundContributionResponse>> getContributions(
            @PathVariable Integer clubId,
            @PathVariable Integer campaignId) {
        return ResponseEntity.ok(fundCampaignService.getContributionsByCampaign(campaignId));
    }

    @PatchMapping("/contributions/{contribId}/quick-tick")
    @Operation(summary = "Thủ quỹ tick xác nhận đã nộp / chưa nộp tiền")
    public ResponseEntity<Void> quickTick(
            @PathVariable Integer clubId,
            @PathVariable Integer contribId,
            @RequestParam boolean isPaid) {
        fundCampaignService.quickTickPayment(contribId, isPaid);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{campaignId}/proof")
    @Operation(summary = "Thành viên tải lên bill chuyển khoản để chờ duyệt")
    public ResponseEntity<Void> uploadProof(
            @PathVariable Integer clubId,
            @PathVariable Integer campaignId,
            @RequestParam String proofUrl) {
        Integer userId = SecurityUtils.getCurrentUserId();
        fundCampaignService.submitProof(campaignId, userId, proofUrl);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{campaignId}/remind")
    @Operation(summary = "Thủ quỹ gửi nhắc nhở hàng loạt tới người chưa đóng tiền")
    public ResponseEntity<Void> remindUnpaid(
            @PathVariable Integer clubId,
            @PathVariable Integer campaignId) {
        fundCampaignService.remindUnpaidMembers(campaignId);
        return ResponseEntity.ok().build();
    }
}
