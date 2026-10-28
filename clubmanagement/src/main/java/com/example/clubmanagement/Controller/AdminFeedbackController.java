package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.FeedbackService;
import com.example.clubmanagement.dto.FeedbackResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * API quản lý feedback dành cho Admin (đường dẫn /api/admin/** đã yêu cầu ROLE_ADMIN trong SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/feedbacks")
@Tag(name = "Admin Feedback", description = "Admin xem danh sách, thống kê và xoá feedback hệ thống")
public class AdminFeedbackController {

    private final FeedbackService feedbackService;

    public AdminFeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** GET /api/admin/feedbacks?rating=&page=&size= */
    @GetMapping
    @Operation(summary = "Danh sách feedback", description = "Sắp xếp mới nhất trước, có thể lọc theo số sao")
    public ResponseEntity<?> getAllFeedbacks(
            @Parameter(description = "Lọc theo số sao (1-5)") @RequestParam(required = false) Integer rating,
            @Parameter(description = "Trang (bắt đầu từ 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Số phần tử mỗi trang (tối đa 100)") @RequestParam(defaultValue = "20") int size) {
        return FeedbackController.handle(() -> {
            Page<FeedbackResponse> result = feedbackService.getAllFeedbacks(rating, page, size);
            return ResponseEntity.ok(Map.of(
                    "content", result.getContent(),
                    "page", result.getNumber(),
                    "size", result.getSize(),
                    "totalElements", result.getTotalElements(),
                    "totalPages", result.getTotalPages()
            ));
        });
    }

    /** GET /api/admin/feedbacks/summary */
    @GetMapping("/summary")
    @Operation(summary = "Thống kê feedback", description = "Tổng số, điểm trung bình và số lượng theo từng mức sao")
    public ResponseEntity<?> getSummary() {
        return FeedbackController.handle(() -> ResponseEntity.ok(feedbackService.getSummary()));
    }

    /** DELETE /api/admin/feedbacks/{id} */
    @DeleteMapping("/{id}")
    @Operation(summary = "Xoá feedback (vi phạm / spam)")
    public ResponseEntity<?> deleteFeedback(@PathVariable Integer id) {
        return FeedbackController.handle(() -> {
            feedbackService.deleteFeedbackById(id);
            return ResponseEntity.ok(Map.of("message", "Đã xoá feedback thành công!"));
        });
    }
}
