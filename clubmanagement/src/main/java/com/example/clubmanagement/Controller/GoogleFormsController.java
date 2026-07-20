package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.GoogleForm;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Service.GoogleFormsService;
import com.example.clubmanagement.dto.GoogleFormQuestionRequest;
import com.example.clubmanagement.dto.GoogleFormResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller quản lý Google Form theo môi trường CLB.
 *
 * <p>Phân quyền:
 * <ul>
 *   <li>Mọi request đều yêu cầu userId đã liên kết Google Account.</li>
 *   <li>Mọi request đều yêu cầu userId là thành viên ACTIVE của clubId.</li>
 *   <li>Tạo / Xóa: chỉ PRESIDENT hoặc TREASURER.</li>
 *   <li>Xem danh sách / Chi tiết / Responses: mọi thành viên ACTIVE.</li>
 *   <li>Thêm câu hỏi (comment): mọi thành viên ACTIVE.</li>
 * </ul>
 */
@Tag(name = "Google Forms", description = "Quản lý Google Form theo CLB với phân quyền RBAC")
@RestController
@RequestMapping("/api/google/forms")
public class GoogleFormsController {

    private final GoogleFormsService googleFormsService;

    public GoogleFormsController(GoogleFormsService googleFormsService) {
        this.googleFormsService = googleFormsService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/google/forms/create
    // Yêu cầu: PRESIDENT hoặc TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Tạo mới một Google Form cho CLB.
     * Chỉ PRESIDENT hoặc TREASURER của CLB đó mới được thực hiện.
     *
     * POST /api/google/forms/create?userId=1&clubId=10&title=TenForm&type=EVENT
     */
    @Operation(summary = "Tạo Google Form mới trong CLB",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền tạo.")
    @PostMapping("/create")
    public ResponseEntity<?> createForm(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "Tiêu đề form") @RequestParam String title,
            @Parameter(description = "Loại: EVENT hoặc CLUB_ACTIVITIES") @RequestParam SheetFormType type) {
        try {
            GoogleForm form = googleFormsService.createForm(userId, clubId, title, type);
            return ResponseEntity.ok(mapToResponse(form));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/google/forms/list
    // Yêu cầu: mọi thành viên ACTIVE của CLB
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách Google Form thuộc CLB.
     * Chỉ hiển thị form của CLB đó — không lộ form của CLB khác.
     *
     * GET /api/google/forms/list?userId=1&clubId=10
     */
    @Operation(summary = "Lấy danh sách Google Form của CLB",
               description = "Mọi thành viên ACTIVE được xem. Chỉ thấy form trong CLB mình.")
    @GetMapping("/list")
    public ResponseEntity<?> getForms(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId) {
        try {
            List<GoogleFormResponse> forms = googleFormsService.getFormsByClub(userId, clubId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(forms);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/google/forms/{formId}
    // Yêu cầu: mọi thành viên ACTIVE của CLB
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy chi tiết cấu trúc câu hỏi của Google Form.
     * Form phải thuộc CLB mà người dùng đang hoạt động.
     *
     * GET /api/google/forms/{formId}?userId=1&clubId=10
     */
    @Operation(summary = "Xem chi tiết Google Form",
               description = "Mọi thành viên ACTIVE được xem. Form phải thuộc CLB của người dùng.")
    @GetMapping("/{formId}")
    public ResponseEntity<?> getFormDetails(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            Map<String, Object> details = googleFormsService.getFormDetails(userId, clubId, formId);
            return ResponseEntity.ok(details);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/google/forms/{formId}/responses
    // Yêu cầu: mọi thành viên ACTIVE của CLB
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy danh sách phản hồi từ Google Form.
     * Form phải thuộc CLB mà người dùng đang hoạt động.
     *
     * GET /api/google/forms/{formId}/responses?userId=1&clubId=10
     */
    @Operation(summary = "Xem responses của Google Form",
               description = "Mọi thành viên ACTIVE được xem responses. Form phải thuộc CLB của người dùng.")
    @GetMapping("/{formId}/responses")
    public ResponseEntity<?> getFormResponses(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            Map<String, Object> responses = googleFormsService.getFormResponses(userId, clubId, formId);
            return ResponseEntity.ok(responses);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/google/forms/{formId}/questions
    // Yêu cầu: mọi thành viên ACTIVE (MEMBER cũng được comment)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Thêm một câu hỏi vào Google Form (tương đương "comment").
     * Mọi thành viên ACTIVE đều có thể thực hiện — kể cả MEMBER.
     *
     * POST /api/google/forms/{formId}/questions?userId=1&clubId=10
     */
    @Operation(summary = "Thêm câu hỏi vào Google Form",
               description = "Mọi thành viên ACTIVE đều được thêm câu hỏi (PRESIDENT, TREASURER và MEMBER).")
    @PostMapping("/{formId}/questions")
    public ResponseEntity<?> addQuestion(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId,
            @RequestBody GoogleFormQuestionRequest questionRequest) {
        try {
            String result = googleFormsService.addQuestion(userId, clubId, formId, questionRequest);
            return ResponseEntity.ok(Map.of("message", "Thêm câu hỏi thành công!", "response", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/google/forms/{formId}
    // Yêu cầu: PRESIDENT hoặc TREASURER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Xóa Google Form khỏi hệ thống và Google Drive.
     * Chỉ PRESIDENT hoặc TREASURER của CLB mới được xóa.
     *
     * DELETE /api/google/forms/{formId}?userId=1&clubId=10
     */
    @Operation(summary = "Xóa Google Form",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền xóa.")
    @DeleteMapping("/{formId}")
    public ResponseEntity<?> deleteForm(
            @Parameter(description = "ID người dùng") @RequestParam Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            googleFormsService.deleteForm(userId, clubId, formId);
            return ResponseEntity.ok(Map.of("message", "Xóa Google Form thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helper mapper
    // ─────────────────────────────────────────────────────────────────────────

    private GoogleFormResponse mapToResponse(GoogleForm form) {
        if (form == null) return null;
        return GoogleFormResponse.builder()
                .id(form.getId())
                .formId(form.getFormId())
                .title(form.getTitle())
                .type(form.getType())
                .formUrl(form.getFormUrl())
                .responderUri(form.getResponderUri())
                .userId(form.getUser() != null ? form.getUser().getUserId() : null)
                .clubId(form.getClub() != null ? form.getClub().getId() : null)
                .createdAt(form.getCreatedAt())
                .updatedAt(form.getUpdatedAt())
                .build();
    }
}
