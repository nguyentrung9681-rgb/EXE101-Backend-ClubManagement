package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.ChatService;
import com.example.clubmanagement.dto.*;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.nio.file.Paths;

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
                                                    @RequestParam Integer requesterUserId) {
        try {
            MessageCursorPageResponse response = chatService.getDepartmentMessages(clubId, departmentId, requesterUserId, limit, beforeMessageId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
                                                     @RequestParam Integer requesterUserId) {
        try {
            ChatMessageResponse response = chatService.sendDepartmentMessage(clubId, departmentId, requesterUserId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
                                              @RequestParam Integer requesterUserId) {
        try {
            MessageCursorPageResponse response = chatService.getClubMessages(clubId, requesterUserId, limit, beforeMessageId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 4. Gửi tin nhắn mới vào Kênh CHUNG CLB
     * POST /api/chat/clubs/{clubId}/messages?requesterUserId={id}
     */
    @PostMapping("/clubs/{clubId}/messages")
    public ResponseEntity<?> sendClubMessage(@PathVariable Integer clubId,
                                              @RequestBody ChatMessageRequest request,
                                              @RequestParam Integer requesterUserId) {
        try {
            ChatMessageResponse response = chatService.sendClubMessage(clubId, requesterUserId, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 5. Upload tệp đính kèm (Hình ảnh/Tài liệu)
     * POST /api/chat/upload
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            ChatAttachmentDto response = chatService.uploadAttachment(file);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 6. Truy xuất tệp đính kèm đã upload
     * GET /api/chat/files/{fileName}
     */
    @GetMapping("/files/{fileName:.+}")
    public ResponseEntity<?> getFile(@PathVariable String fileName) {
        try {
            Path filePath = Paths.get("uploads/chat/").resolve(fileName).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Không thể xem file: " + e.getMessage());
        }
    }

    /**
     * 7. Ghim / Bỏ ghim tin nhắn
     * PUT /api/chat/messages/{messageId}/pin?requesterUserId={id}
     */
    @PutMapping("/messages/{messageId}/pin")
    public ResponseEntity<?> togglePinMessage(@PathVariable Long messageId,
                                               @RequestParam Integer requesterUserId) {
        try {
            ChatMessageResponse response = chatService.togglePinMessage(messageId, requesterUserId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * 8. Xóa mềm tin nhắn
     * DELETE /api/chat/messages/{messageId}?requesterUserId={id}
     */
    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<?> deleteMessage(@PathVariable Long messageId,
                                            @RequestParam Integer requesterUserId) {
        try {
            chatService.deleteMessage(messageId, requesterUserId);
            return ResponseEntity.ok("Xóa tin nhắn thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
