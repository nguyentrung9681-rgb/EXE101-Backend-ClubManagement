package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.ClubDocument;
import com.example.clubmanagement.Entity.DocumentRevision;
import com.example.clubmanagement.Enum.DocumentCategory;
import com.example.clubmanagement.Enum.DocumentType;
import com.example.clubmanagement.Service.ClubDocumentService;
import com.example.clubmanagement.dto.ClubDocumentRequest;
import com.example.clubmanagement.dto.ClubDocumentResponse;
import com.example.clubmanagement.dto.DocumentCategoryResponse;
import com.example.clubmanagement.dto.DocumentRevisionResponse;
import com.example.clubmanagement.dto.DocumentTypeResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/documents")
public class ClubDocumentController {

    private final ClubDocumentService clubDocumentService;

    public ClubDocumentController(ClubDocumentService clubDocumentService) {
        this.clubDocumentService = clubDocumentService;
    }

    /**
     * Lấy danh sách tất cả các loại tài liệu (documentType).
     * GET /api/documents/types
     */
    @GetMapping("/types")
    public ResponseEntity<List<DocumentTypeResponse>> getDocumentTypes() {
        List<DocumentTypeResponse> types = Arrays.stream(DocumentType.values())
                .map(type -> DocumentTypeResponse.builder()
                        .value(type.name())
                        .displayName(type.getDisplayName())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(types);
    }

    /**
     * Lấy danh sách các danh mục tài liệu phục vụ tab lọc trên trang web (EVENT, CLUB_ACTIVITY).
     * GET /api/documents/categories
     */
    @GetMapping("/categories")
    public ResponseEntity<List<DocumentCategoryResponse>> getDocumentCategories() {
        List<DocumentCategoryResponse> categories = Arrays.stream(DocumentCategory.values())
                .map(cat -> DocumentCategoryResponse.builder()
                        .value(cat.name())
                        .displayName(cat.getDisplayName())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(categories);
    }

    /**
     * Tạo tài liệu mới và liên kết Google Doc.
     * POST /api/documents?userId=1&clubId=2&title=xxx&category=EVENT&type=MEETING_MINUTES
     */
    @PostMapping
    public ResponseEntity<?> createDocument(
            @RequestParam Integer userId,
            @RequestParam Integer clubId,
            @RequestParam(required = false) Integer eventId,
            @RequestParam String title,
            @Parameter(description = "Danh mục lọc trên web", schema = @Schema(allowableValues = {"EVENT", "CLUB_ACTIVITY"}))
            @RequestParam(required = false) DocumentCategory category,
            @Parameter(description = "Loại tài liệu", schema = @Schema(allowableValues = {"MEETING_MINUTES", "EVENT_PLAN", "REPORT", "FINANCE", "OTHER"}))
            @RequestParam DocumentType type
    ) {
        try {
            ClubDocumentRequest request = ClubDocumentRequest.builder()
                    .clubId(clubId)
                    .eventId(eventId)
                    .title(title)
                    .category(category != null ? category.name() : null)
                    .documentType(type.name())
                    .build();
            ClubDocument doc = clubDocumentService.createDocument(request, userId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách tài liệu của một Câu lạc bộ có hỗ trợ lọc, tìm kiếm và sắp xếp.
     * GET /api/documents/club/{clubId}?userId=1&search=xyz&category=EVENT&type=MEETING_MINUTES&sortBy=title&sortDir=asc
     */
    @GetMapping("/club/{clubId}")
    public ResponseEntity<?> getClubDocuments(
            @PathVariable Integer clubId,
            @RequestParam Integer userId,
            @RequestParam(required = false) String search,
            @Parameter(description = "Danh mục lọc trên web (Tab: Event / Club Activity)", schema = @Schema(allowableValues = {"EVENT", "CLUB_ACTIVITY"}))
            @RequestParam(required = false) String category,
            @Parameter(description = "Loại tài liệu", schema = @Schema(allowableValues = {"MEETING_MINUTES", "EVENT_PLAN", "REPORT", "FINANCE", "OTHER"}))
            @RequestParam(required = false) String type,
            @Parameter(description = "Sắp xếp theo trường", schema = @Schema(allowableValues = {"title", "date"}))
            @RequestParam(required = false) String sortBy,
            @Parameter(description = "Chiều sắp xếp", schema = @Schema(allowableValues = {"asc", "desc"}))
            @RequestParam(required = false) String sortDir
    ) {
        try {
            List<ClubDocumentResponse> list = clubDocumentService.getDocumentsByClubFiltered(clubId, search, category, type, sortBy, sortDir, userId).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Chi tiết tài liệu (kèm phần tóm tắt).
     * GET /api/documents/{id}?userId=1
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Integer id, @RequestParam Integer userId) {
        try {
            ClubDocument doc = clubDocumentService.getDocumentById(id, userId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Đồng bộ nội dung tài liệu thủ công từ Google Docs về DB nội bộ.
     * POST /api/documents/{id}/sync?userId=1
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<?> syncDocument(@PathVariable Integer id, @RequestParam Integer userId) {
        try {
            ClubDocument doc = clubDocumentService.syncDocumentContent(id, userId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Đồng bộ thất bại: " + e.getMessage());
        }
    }

    /**
     * Lấy lịch sử chỉnh sửa (các Revisions) của tài liệu.
     * GET /api/documents/{id}/revisions?userId=1
     */
    @GetMapping("/{id}/revisions")
    public ResponseEntity<?> getRevisions(@PathVariable Integer id, @RequestParam Integer userId) {
        try {
            List<DocumentRevisionResponse> responses = clubDocumentService.getDocumentRevisions(id, userId).stream()
                    .map(this::mapToRevisionResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Webhook nhận thông báo thay đổi từ Google Drive.
     * POST /api/documents/webhook
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> receiveGoogleWebhook(
            @RequestHeader(value = "X-Goog-Channel-ID", required = false) String channelId,
            @RequestHeader(value = "X-Goog-Resource-ID", required = false) String resourceId,
            @RequestHeader(value = "X-Goog-Resource-State", required = false) String resourceState
    ) {
        if ("sync".equalsIgnoreCase(resourceState)) {
            return ResponseEntity.ok().build(); // Xác thực kênh watch ban đầu của Google
        }

        if (channelId != null && resourceId != null) {
            // Chạy bất đồng bộ đồng bộ dữ liệu để tránh nghẽn webhook của Google
            new Thread(() -> clubDocumentService.syncDocumentByChannel(channelId, resourceId)).start();
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Cấu hình quyền chia sẻ tài liệu Google Doc và trả về link chia sẻ.
     * POST /api/documents/{id}/share?role=commenter&userId=1
     */
    @PostMapping("/{id}/share")
    public ResponseEntity<?> shareDocument(
            @PathVariable Integer id,
            @Parameter(description = "Quyền truy cập", schema = @Schema(allowableValues = {"reader", "commenter", "writer"}))
            @RequestParam(required = false, defaultValue = "commenter") String role,
            @RequestParam Integer userId
    ) {
        try {
            String shareUrl = clubDocumentService.shareDocument(id, role, userId);
            return ResponseEntity.ok(java.util.Map.of(
                    "documentUrl", shareUrl,
                    "role", role,
                    "message", "Chia sẻ tài liệu thành công với vai trò: " + role
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi chia sẻ: " + e.getMessage());
        }
    }

    /**
     * Xóa tài liệu khỏi hệ thống và Google Docs.
     * DELETE /api/documents/{id}?userId=1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable Integer id,
            @RequestParam Integer userId
    ) {
        try {
            clubDocumentService.deleteDocument(id, userId);
            return ResponseEntity.ok("Xóa tài liệu thành công");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi xóa tài liệu: " + e.getMessage());
        }
    }

    private ClubDocumentResponse mapToResponse(ClubDocument doc) {
        if (doc == null) return null;
        String catName = doc.getCategory() != null ? doc.getCategory().name() : (doc.getEvent() != null ? DocumentCategory.EVENT.name() : DocumentCategory.CLUB_ACTIVITY.name());
        return ClubDocumentResponse.builder()
                .id(doc.getId())
                .clubId(doc.getClub() != null ? doc.getClub().getId() : null)
                .clubName(doc.getClub() != null ? doc.getClub().getName() : null)
                .eventId(doc.getEvent() != null ? doc.getEvent().getId() : null)
                .eventTitle(doc.getEvent() != null ? doc.getEvent().getTitle() : null)
                .title(doc.getTitle())
                .category(catName)
                .documentType(doc.getDocumentType() != null ? doc.getDocumentType().name() : null)
                .googleDocumentId(doc.getGoogleDocumentId())
                .documentUrl(doc.getDocumentUrl())
                .syncStatus(doc.getSyncStatus() != null ? doc.getSyncStatus().name() : null)
                .contentSummary(doc.getContentSummary())
                .createdByUserId(doc.getCreatedBy() != null ? doc.getCreatedBy().getUserId() : null)
                .createdByName(doc.getCreatedBy() != null ? doc.getCreatedBy().getFullName() : null)
                .createdAt(doc.getCreatedAt())
                .updatedAt(doc.getUpdatedAt())
                .build();
    }

    private DocumentRevisionResponse mapToRevisionResponse(DocumentRevision revision) {
        if (revision == null) return null;
        return DocumentRevisionResponse.builder()
                .id(revision.getId())
                .version(revision.getVersion())
                .content(revision.getContent())
                .syncedAt(revision.getSyncedAt())
                .build();
    }
}
