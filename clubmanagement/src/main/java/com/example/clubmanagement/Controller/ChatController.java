package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.ChatService;
import com.example.clubmanagement.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * 1. Lấy danh sách tin nhắn Kênh PHÒNG BAN (Lazy loading Cursor Pagination)
     * GET /api/chat/clubs/{clubId}/departments/{departmentId}/messages?limit=50&beforeMessageId=123&requesterUserId={id}
     */
    @GetMapping("/clubs/{clubId}/departments/{departmentId}/messages")
    public ResponseEntity<?> getDepartmentMessages(@PathVariable Integer clubId,
                                                    @PathVariable Integer departmentId,
                                                    @RequestParam(required = false, defaultValue = "50") Integer limit,
                                                    @RequestParam(required = false) Long beforeMessageId,
                                                    @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            MessageCursorPageResponse response = chatService.getDepartmentMessages(clubId, departmentId, effectiveUserId, limit, beforeMessageId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 2. Gửi tin nhắn mới vào Kênh PHÒNG BAN
     * POST /api/chat/clubs/{clubId}/departments/{departmentId}/messages?requesterUserId={id}
     */
    @PostMapping("/clubs/{clubId}/departments/{departmentId}/messages")
    public ResponseEntity<?> sendDepartmentMessage(@PathVariable Integer clubId,
                                                     @PathVariable Integer departmentId,
                                                     @RequestBody ChatMessageRequest request,
                                                     @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            ChatMessageResponse response = chatService.sendDepartmentMessage(clubId, departmentId, effectiveUserId, request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 3. Lấy danh sách tin nhắn Kênh CHUNG CLB (Lazy loading Cursor Pagination)
     * GET /api/chat/clubs/{clubId}/messages?limit=50&beforeMessageId=123&requesterUserId={id}
     */
    @GetMapping("/clubs/{clubId}/messages")
    public ResponseEntity<?> getClubMessages(@PathVariable Integer clubId,
                                              @RequestParam(required = false, defaultValue = "50") Integer limit,
                                              @RequestParam(required = false) Long beforeMessageId,
                                              @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            MessageCursorPageResponse response = chatService.getClubMessages(clubId, effectiveUserId, limit, beforeMessageId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 4. Gửi tin nhắn mới vào Kênh CHUNG CLB
     * POST /api/chat/clubs/{clubId}/messages?requesterUserId={id}
     */
    @PostMapping("/clubs/{clubId}/messages")
    public ResponseEntity<?> sendClubMessage(@PathVariable Integer clubId,
                                              @RequestBody ChatMessageRequest request,
                                              @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            ChatMessageResponse response = chatService.sendClubMessage(clubId, effectiveUserId, request);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 5. Upload tệp đính kèm (Hình ảnh/Tài liệu)
     * POST /api/chat/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            SecurityUtils.getCurrentUser().orElseThrow(() -> 
                new SecurityException("Yêu cầu chưa xác thực! Vui lòng đăng nhập trước khi tải file."));
            ChatAttachmentDto response = chatService.uploadAttachment(file);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 6. Truy xuất tệp đính kèm đã upload (Ngăn chặn Path Traversal & Stored XSS)
     * GET /api/chat/files/{fileName}
     */
    @GetMapping("/files/{fileName:.+}")
    public ResponseEntity<?> getFile(@PathVariable String fileName) {
        try {
            Path baseDirPath = Paths.get("uploads/chat/").toAbsolutePath().normalize();
            Path filePath = baseDirPath.resolve(fileName).normalize();

            // Lỗ hổng Path Traversal check: Đảm bảo file được tải nằm trong thư mục uploads/chat/
            if (!filePath.startsWith(baseDirPath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Truy cập đường dẫn tệp không hợp lệ!"));
            }

            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            String contentType = null;
            try {
                contentType = java.nio.file.Files.probeContentType(filePath);
            } catch (Exception ignored) {}

            String disposition = "attachment";
            if (contentType != null && (contentType.startsWith("image/") || contentType.equals("application/pdf"))) {
                disposition = "inline";
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Không thể xem file: " + e.getMessage()));
        }
    }

    /**
     * 7. Ghim / Bỏ ghim tin nhắn
     * PUT /api/chat/messages/{messageId}/pin?requesterUserId={id}
     */
    @PutMapping("/messages/{messageId}/pin")
    public ResponseEntity<?> togglePinMessage(@PathVariable Long messageId,
                                               @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            ChatMessageResponse response = chatService.togglePinMessage(messageId, effectiveUserId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 8. Xóa mềm tin nhắn
     * DELETE /api/chat/messages/{messageId}?requesterUserId={id}
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long messageId,
                                            @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            chatService.deleteMessage(messageId, effectiveUserId);
            return ResponseEntity.ok(Map.of("message", "Xóa tin nhắn thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 9. Lấy danh sách các Emoji được hỗ trợ phân theo danh mục cho giao diện chọn Emoji (Emoji Picker)
     * GET /api/chat/emojis
     */
    @io.swagger.v3.oas.annotations.Operation(summary = "Lấy danh sách Emoji phân theo danh mục (cho Emoji Picker)")
    @GetMapping("/emojis")
    public ResponseEntity<?> getAvailableEmojis() {
        try {
            return ResponseEntity.ok(chatService.getAvailableEmojis());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
