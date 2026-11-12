package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.AdminOrderService;
import com.example.clubmanagement.dto.AdminOrderResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/**
 * Sổ giao dịch của Admin (đường dẫn /api/admin/** đã yêu cầu ROLE_ADMIN trong SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/orders")
@CrossOrigin(origins = "*")
@Tag(name = "Admin Orders", description = "Liệt kê từng đơn thanh toán kèm mã tham chiếu để đối chiếu sao kê cổng thanh toán")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    /** GET /api/admin/orders?status=PAID&search=&from=2026-11-01&to=2026-11-09&page=0&size=20 */
    @GetMapping
    @Operation(summary = "Danh sách đơn thanh toán",
            description = "Mỗi đơn có orderCode, paymentMethod, transactionNo (mã tham chiếu cổng) và referenceSource "
                    + "(GATEWAY = đối chiếu được sao kê, MANUAL = xác nhận tay). Kèm tên/email/sđt khách để spot-check. "
                    + "Lọc theo ngày tạo đơn, sắp xếp mới nhất trước.")
    public ResponseEntity<?> getOrders(
            @Parameter(description = "Trạng thái đơn: PENDING, PAID, CANCELLED, EXPIRED")
            @RequestParam(required = false) String status,
            @Parameter(description = "Tìm theo tên/email khách, tên CLB, orderCode hoặc mã tham chiếu")
            @RequestParam(required = false) String search,
            @Parameter(description = "Từ ngày tạo đơn (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "Đến hết ngày tạo đơn (yyyy-MM-dd)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "Trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        try {
            Page<AdminOrderResponse> result = adminOrderService.getOrders(status, search, from, to, page, size);
            return ResponseEntity.ok(Map.of(
                    "content", result.getContent(),
                    "page", result.getNumber(),
                    "size", result.getSize(),
                    "totalElements", result.getTotalElements(),
                    "totalPages", result.getTotalPages()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "Lỗi hệ thống khi tải danh sách đơn"));
        }
    }
}