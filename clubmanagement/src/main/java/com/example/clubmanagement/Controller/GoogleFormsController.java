package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.GoogleForm;
import com.example.clubmanagement.Entity.SheetFormType;
import com.example.clubmanagement.Service.GoogleFormsService;
import com.example.clubmanagement.dto.GoogleFormQuestionRequest;
import com.example.clubmanagement.dto.GoogleFormResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/google/forms")
public class GoogleFormsController {

    private final GoogleFormsService googleFormsService;

    public GoogleFormsController(GoogleFormsService googleFormsService) {
        this.googleFormsService = googleFormsService;
    }

    /**
     * Lấy URL kết nối tài khoản Google có quyền truy cập Google Forms và Drive.
     * GET /api/google/forms/connect?userId=1
     */
   /** @GetMapping("/connect")
    public ResponseEntity<?> connect(@RequestParam Integer userId) {
        try {
            String url = googleFormsService.getAuthorizeUrl(userId);
            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Tạo mới một Google Form cho người dùng và lưu vào database hệ thống.
     * POST /api/google/forms/create?userId=1&title=TenForm&type=EVENT
     * type bắt buộc: EVENT hoặc CLUB_ACTIVITIES
     */
    @PostMapping("/create")
    public ResponseEntity<?> createForm(@RequestParam Integer userId,
                                        @RequestParam String title,
                                        @RequestParam SheetFormType type) {
        try {
            GoogleForm form = googleFormsService.createForm(userId, title, type);
            return ResponseEntity.ok(mapToResponse(form));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách các biểu mẫu được tạo trong hệ thống của người dùng.
     * GET /api/google/forms/list?userId=1
     */
    @GetMapping("/list")
    public ResponseEntity<?> getForms(@RequestParam Integer userId) {
        try {
            List<GoogleFormResponse> forms = googleFormsService.getFormsByUser(userId).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(forms);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Lấy chi tiết cấu trúc câu hỏi của Google Form.
     * GET /api/google/forms/{formId}?userId=1
     */
    @GetMapping("/{formId}")
    public ResponseEntity<?> getFormDetails(@RequestParam Integer userId, @PathVariable String formId) {
        try {
            Map<String, Object> details = googleFormsService.getFormDetails(userId, formId);
            return ResponseEntity.ok(details);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách phản hồi từ Google Form.
     * GET /api/google/forms/{formId}/responses?userId=1
     */
    @GetMapping("/{formId}/responses")
    public ResponseEntity<?> getFormResponses(@RequestParam Integer userId, @PathVariable String formId) {
        try {
            Map<String, Object> responses = googleFormsService.getFormResponses(userId, formId);
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Thêm một câu hỏi vào Google Form.
     * POST /api/google/forms/{formId}/questions?userId=1
     */
    @PostMapping("/{formId}/questions")
    public ResponseEntity<?> addQuestion(@RequestParam Integer userId,
                                         @PathVariable String formId,
                                         @RequestBody GoogleFormQuestionRequest questionRequest) {
        try {
            String result = googleFormsService.addQuestion(userId, formId, questionRequest);
            return ResponseEntity.ok(Map.of("message", "Thêm câu hỏi thành công!", "response", result));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }

    /**
     * Xóa Google Form khỏi hệ thống và Drive cá nhân của người dùng.
     * DELETE /api/google/forms/{formId}?userId=1
     */
    @DeleteMapping("/{formId}")
    public ResponseEntity<?> deleteForm(@RequestParam Integer userId, @PathVariable String formId) {
        try {
            googleFormsService.deleteForm(userId, formId);
            return ResponseEntity.ok(Map.of("message", "Xóa Google Form thành công!"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
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
                .userId(form.getUser() != null ? form.getUser().getUserId() : null)
                .createdAt(form.getCreatedAt())
                .updatedAt(form.getUpdatedAt())
                .build();
    }
}
