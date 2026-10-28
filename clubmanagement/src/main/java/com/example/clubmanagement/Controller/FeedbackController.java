package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.FeedbackService;
import com.example.clubmanagement.dto.FeedbackRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

/**
 * API feedback hệ thống dành cho người dùng đã đăng nhập.
 * Mỗi người dùng có tối đa 1 feedback (số sao 1-5 + nhận xét), được phép xem / sửa / xoá.
 */
@RestController
@RequestMapping("/api/feedbacks")
@Tag(name = "Feedback", description = "Người dùng đánh giá hệ thống S-Club (1-5 sao + nhận xét)")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /** GET /api/feedbacks/rating-options - Danh sách mức sao 1-5 để FE render nút chọn sao. */
    @GetMapping("/rating-options")
    @Operation(summary = "Danh sách lựa chọn số sao", description = "Trả về 5 mức [{value, label}], FE dùng để hiển thị nút chọn sao; gửi 'value' đã chọn vào trường rating.")
    public ResponseEntity<?> getRatingOptions() {
        return ResponseEntity.ok(feedbackService.getRatingOptions());
    }

    /** POST /api/feedbacks - Gửi feedback mới. */
    @PostMapping
    @Operation(summary = "Gửi feedback", description = "rating bắt buộc - là 'value' người dùng chọn từ /api/feedbacks/rating-options (1-5, 5 là tốt nhất), comment tuỳ chọn (≤ 2000 ký tự). Mỗi user chỉ gửi 1 lần.")
    public ResponseEntity<?> createFeedback(@RequestBody FeedbackRequest request) {
        return handle(() -> ResponseEntity.status(HttpStatus.CREATED)
                .body(feedbackService.createFeedback(currentUserId(), request)));
    }

    /** GET /api/feedbacks/me - Xem feedback của mình. */
    @GetMapping("/me")
    @Operation(summary = "Xem feedback của tôi")
    public ResponseEntity<?> getMyFeedback() {
        return handle(() -> ResponseEntity.ok(feedbackService.getMyFeedback(currentUserId())));
    }

    /** PUT /api/feedbacks/me - Cập nhật feedback của mình. */
    @PutMapping("/me")
    @Operation(summary = "Cập nhật feedback của tôi")
    public ResponseEntity<?> updateMyFeedback(@RequestBody FeedbackRequest request) {
        return handle(() -> ResponseEntity.ok(feedbackService.updateMyFeedback(currentUserId(), request)));
    }

    /** DELETE /api/feedbacks/me - Xoá feedback của mình. */
    @DeleteMapping("/me")
    @Operation(summary = "Xoá feedback của tôi")
    public ResponseEntity<?> deleteMyFeedback() {
        return handle(() -> {
            feedbackService.deleteMyFeedback(currentUserId());
            return ResponseEntity.ok(Map.of("message", "Đã xoá feedback thành công!"));
        });
    }

    private Integer currentUserId() {
        Integer userId = SecurityUtils.getCurrentUserId();
        if (userId == null) {
            throw new SecurityException("Yêu cầu không được xác thực! Vui lòng cung cấp JWT Token hợp lệ.");
        }
        return userId;
    }

    static ResponseEntity<?> handle(Supplier<ResponseEntity<?>> action) {
        try {
            return action.get();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
