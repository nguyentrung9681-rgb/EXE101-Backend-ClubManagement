package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
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
 * Controller quản lý Google Form theo môi trường CLB với phân quyền RBAC và xác thực JWT.
 */
@Tag(name = "Google Forms", description = "Quản lý Google Form theo CLB với phân quyền RBAC")
@RestController
@RequestMapping("/api/google/forms")
public class GoogleFormsController {

    private final GoogleFormsService googleFormsService;

    public GoogleFormsController(GoogleFormsService googleFormsService) {
        this.googleFormsService = googleFormsService;
    }

    @Operation(summary = "Tạo Google Form mới trong CLB",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền tạo.")
    @PostMapping("/create")
    public ResponseEntity<?> createForm(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "Tiêu đề form") @RequestParam String title,
            @Parameter(description = "Loại: EVENT hoặc CLUB_ACTIVITIES") @RequestParam SheetFormType type) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GoogleForm form = googleFormsService.createForm(effectiveUserId, clubId, title, type);
            return ResponseEntity.ok(mapToResponse(form));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy danh sách Google Form của CLB",
               description = "Mọi thành viên ACTIVE được xem. Chỉ thấy form trong CLB mình.")
    @GetMapping("/list")
    public ResponseEntity<?> getForms(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<GoogleFormResponse> forms = googleFormsService.getFormsByClub(effectiveUserId, clubId)
                    .stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(forms);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Xem chi tiết Google Form",
               description = "Mọi thành viên ACTIVE được xem. Form phải thuộc CLB của người dùng.")
    @GetMapping("/{formId}")
    public ResponseEntity<?> getFormDetails(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            Map<String, Object> details = googleFormsService.getFormDetails(effectiveUserId, clubId, formId);
            return ResponseEntity.ok(details);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Xem responses của Google Form",
               description = "Mọi thành viên ACTIVE được xem responses. Form phải thuộc CLB của người dùng.")
    @GetMapping("/{formId}/responses")
    public ResponseEntity<?> getFormResponses(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            Map<String, Object> responses = googleFormsService.getFormResponses(effectiveUserId, clubId, formId);
            return ResponseEntity.ok(responses);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Thêm câu hỏi vào Google Form",
               description = "Mọi thành viên ACTIVE đều được thêm câu hỏi (PRESIDENT, TREASURER và MEMBER).")
    @PostMapping("/{formId}/questions")
    public ResponseEntity<?> addQuestion(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId,
            @RequestBody GoogleFormQuestionRequest questionRequest) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            String result = googleFormsService.addQuestion(effectiveUserId, clubId, formId, questionRequest);
            return ResponseEntity.ok(Map.of("message", "Thêm câu hỏi thành công!", "response", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Đồng bộ phản hồi Form sang Google Sheet liên kết",
               description = "Mọi thành viên ACTIVE của CLB có thể thực hiện đồng bộ câu trả lời mới nhất sang Google Sheet.")
    @PostMapping("/{formId}/sync-sheet")
    public ResponseEntity<?> syncSheet(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            String result = googleFormsService.syncFormResponsesToSheet(effectiveUserId, clubId, formId);
            return ResponseEntity.ok(Map.of("message", "Đồng bộ câu trả lời sang Google Sheet thành công!", "response", result));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật tiêu đề Google Form",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền cập nhật tiêu đề.")
    @PutMapping("/{formId}/title")
    public ResponseEntity<?> updateFormTitle(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId,
            @Parameter(description = "Tiêu đề mới của form") @RequestParam String title) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GoogleForm form = googleFormsService.updateFormTitle(effectiveUserId, clubId, formId, title);
            return ResponseEntity.ok(mapToResponse(form));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Cập nhật loại của Google Form",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền cập nhật loại (EVENT hoặc CLUB_ACTIVITIES).")
    @PutMapping("/{formId}/type")
    public ResponseEntity<?> updateFormType(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId,
            @Parameter(description = "Loại mới: EVENT hoặc CLUB_ACTIVITIES") @RequestParam SheetFormType type) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GoogleForm form = googleFormsService.updateFormType(effectiveUserId, clubId, formId, type);
            return ResponseEntity.ok(mapToResponse(form));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Xóa Google Form",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền xóa.")
    @DeleteMapping("/{formId}")
    public ResponseEntity<?> deleteForm(
            @Parameter(description = "ID người dùng (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @PathVariable String formId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            googleFormsService.deleteForm(effectiveUserId, clubId, formId);
            return ResponseEntity.ok(Map.of("message", "Xóa Google Form thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    private GoogleFormResponse mapToResponse(GoogleForm form) {
        if (form == null) return null;
        return GoogleFormResponse.builder()
                .id(form.getId())
                .formId(form.getFormId())
                .title(form.getTitle())
                .type(form.getType())
                .formUrl(form.getFormUrl())
                .responderUri(form.getResponderUri())
                .linkedSpreadsheetId(form.getLinkedSpreadsheetId())
                .linkedSpreadsheetUrl(form.getLinkedSpreadsheetUrl())
                .userId(form.getUser() != null ? form.getUser().getUserId() : null)
                .clubId(form.getClub() != null ? form.getClub().getId() : null)
                .createdAt(form.getCreatedAt())
                .updatedAt(form.getUpdatedAt())
                .build();
    }
}
