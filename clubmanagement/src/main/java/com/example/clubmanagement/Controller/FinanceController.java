package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Enum.*;
import com.example.clubmanagement.Service.FinanceService;
import com.example.clubmanagement.dto.FinanceDtos.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/clubs/{clubId}/finance")
@RequiredArgsConstructor
@Tag(name = "Club Finance Management")
public class FinanceController {

    private final FinanceService financeService;

    @GetMapping("/summary")
    @Operation(summary = "Lấy 4 thẻ thống kê tài chính")
    public ResponseEntity<FinanceSummaryResponse> getSummary(@PathVariable Integer clubId) {
        return ResponseEntity.ok(financeService.getSummary(clubId));
    }

    @GetMapping("/overview-chart")
    @Operation(summary = "Dữ liệu biểu đồ doanh thu và chi phí chuỗi thời gian")
    public ResponseEntity<ChartOverviewResponse> getOverviewChart(
            @PathVariable Integer clubId,
            @RequestParam(defaultValue = "6") int periodMonths) {
        return ResponseEntity.ok(financeService.getOverviewChart(clubId, periodMonths));
    }

    @GetMapping("/category-breakdown")
    @Operation(summary = "Tỷ lệ phân bổ chi phí theo danh mục")
    public ResponseEntity<List<CategoryBreakdownResponse>> getCategoryBreakdown(@PathVariable Integer clubId) {
        return ResponseEntity.ok(financeService.getCategoryBreakdown(clubId));
    }

    @PostMapping("/transactions")
    @Operation(summary = "Tạo mới giao dịch thu/chi")
    public ResponseEntity<TransactionResponse> createTransaction(
            @PathVariable Integer clubId,
            @RequestBody TransactionRequest request) {
        Integer currentUserId = SecurityUtils.getCurrentUserId();
        return ResponseEntity.ok(financeService.createTransaction(clubId, currentUserId, request));
    }

    @GetMapping("/transactions")
    @Operation(summary = "Tìm kiếm & lọc danh sách giao dịch có phân trang")
    public ResponseEntity<Page<TransactionResponse>> getTransactions(
            @PathVariable Integer clubId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) TransactionCategory category,
            @RequestParam(required = false) TransactionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(financeService.getTransactions(clubId, search, type, category, status, PageRequest.of(page, size)));
    }

    @PatchMapping("/transactions/{id}/status")
    @Operation(summary = "Duyệt / Từ chối giao dịch")
    public ResponseEntity<TransactionResponse> updateStatus(
            @PathVariable Integer clubId,
            @PathVariable Integer id,
            @RequestParam TransactionStatus status) {
        return ResponseEntity.ok(financeService.updateStatus(id, status));
    }

    @DeleteMapping("/transactions/{id}")
    @Operation(summary = "Xóa giao dịch")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Integer clubId, @PathVariable Integer id) {
        financeService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/export")
    @Operation(summary = "Xuất file Excel danh sách giao dịch")
    public ResponseEntity<byte[]> exportExcel(@PathVariable Integer clubId) throws IOException {
        byte[] data = financeService.exportTransactionsToExcel(clubId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=transactions_club_" + clubId + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
