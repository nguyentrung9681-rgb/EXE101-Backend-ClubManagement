package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
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
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag(name = "Club Documents", description = "Quản lý Google Docs theo CLB với phân quyền RBAC")
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
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(6)).cachePublic()).body(types);
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
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(java.time.Duration.ofHours(6)).cachePublic()).body(categories);
    }

    /**
     * Tạo tài liệu mới và liên kết Google Doc.
     * POST /api/documents?userId=1&clubId=2&title=xxx&category=EVENT&type=MEETING_MINUTES
     */
    @PostMapping
    public ResponseEntity<?> createDocument(
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) Integer clubId,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) String title,
            @Parameter(description = "Danh mục lọc trên web", schema = @Schema(allowableValues = {"EVENT", "CLUB_ACTIVITY"}))
            @RequestParam(required = false) String category,
            @Parameter(description = "Loại tài liệu", schema = @Schema(allowableValues = {"MEETING_MINUTES", "EVENT_PLAN", "REPORT", "FINANCE", "OTHER"}))
            @RequestParam(required = false) String type,
            @RequestBody(required = false) ClubDocumentRequest body
    ) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            Integer finalClubId = (body != null && body.getClubId() != null) ? body.getClubId() : clubId;
            Integer finalEventId = (body != null && body.getEventId() != null) ? body.getEventId() : eventId;
            String finalTitle = (body != null && body.getTitle() != null) ? body.getTitle() : title;
            String finalCategory = (body != null && body.getCategory() != null) ? body.getCategory() : category;
            String finalType = (body != null && body.getDocumentType() != null) ? body.getDocumentType() : type;

            if (finalClubId == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "clubId là bắt buộc!"));
            }
            if (finalTitle == null || finalTitle.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "title là bắt buộc!"));
            }
            if (finalType == null || finalType.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "type là bắt buộc!"));
            }

            ClubDocumentRequest request = ClubDocumentRequest.builder()
                    .clubId(finalClubId)
                    .eventId(finalEventId)
                    .title(finalTitle)
                    .category(finalCategory)
                    .documentType(finalType)
                    .build();
            ClubDocument doc = clubDocumentService.createDocument(request, effectiveUserId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách tài liệu của một Câu lạc bộ có hỗ trợ lọc, tìm kiếm và sắp xếp.
     * GET /api/documents/club/{clubId}?userId=1&search=xyz&category=EVENT&type=MEETING_MINUTES&sortBy=title&sortDir=asc
     */
    @GetMapping("/club/{clubId}")
    public ResponseEntity<?> getClubDocuments(
            @PathVariable Integer clubId,
            @RequestParam(required = false) Integer userId,
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
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<ClubDocumentResponse> list = clubDocumentService.getDocumentsByClubFiltered(clubId, search, category, type, sortBy, sortDir, effectiveUserId).stream()
                    .map(this::mapToResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Chi tiết tài liệu (kèm phần tóm tắt).
     * GET /api/documents/{id}?userId=1
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getDocumentById(@PathVariable Integer id, @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            ClubDocument doc = clubDocumentService.getDocumentById(id, effectiveUserId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Đồng bộ nội dung tài liệu thủ công từ Google Docs về DB nội bộ.
     * POST /api/documents/{id}/sync?userId=1
     */
    @PostMapping("/{id}/sync")
    public ResponseEntity<?> syncDocument(@PathVariable Integer id, @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            ClubDocument doc = clubDocumentService.syncDocumentContent(id, effectiveUserId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Đồng bộ thất bại: " + e.getMessage()));
        }
    }

    /**
     * Lấy lịch sử chỉnh sửa (các Revisions) của tài liệu.
     * GET /api/documents/{id}/revisions?userId=1
     */
    @GetMapping("/{id}/revisions")
    public ResponseEntity<?> getRevisions(@PathVariable Integer id, @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<DocumentRevisionResponse> responses = clubDocumentService.getDocumentRevisions(id, effectiveUserId).stream()
                    .map(this::mapToRevisionResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(responses);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.ok().build();
        }

        if (channelId != null && resourceId != null) {
            clubDocumentService.syncDocumentByChannel(channelId, resourceId);
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
            @RequestParam(required = false) Integer userId
    ) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            String shareUrl = clubDocumentService.shareDocument(id, role, effectiveUserId);
            return ResponseEntity.ok(Map.of(
                    "documentUrl", shareUrl,
                    "role", role,
                    "message", "Chia sẻ tài liệu thành công với vai trò: " + role
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Lỗi chia sẻ: " + e.getMessage()));
        }
    }

    /**
     * Xóa tài liệu khỏi hệ thống và Google Docs.
     * DELETE /api/documents/{id}?userId=1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer userId
    ) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            clubDocumentService.deleteDocument(id, effectiveUserId);
            return ResponseEntity.ok(Map.of("message", "Xóa tài liệu thành công"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Lỗi xóa tài liệu: " + e.getMessage()));
        }
    }

    /**
     * Cập nhật thông tin tài liệu (tiêu đề, danh mục EVENT / CLUB_ACTIVITY, loại tài liệu, sự kiện).
     * Hỗ trợ cả Query Parameters lẫn Request Body JSON.
     * PUT /api/documents/{id}
     */
    @Operation(summary = "Cập nhật tài liệu (tiêu đề, chuyển đổi danh mục EVENT/CLUB_ACTIVITY, loại tài liệu)",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền chỉnh sửa tài liệu.")
    @PutMapping("/{id}")
    public ResponseEntity<?> updateDocument(
            @PathVariable Integer id,
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "Tiêu đề mới") @RequestParam(required = false) String title,
            @Parameter(description = "Danh mục mới (EVENT hoặc CLUB_ACTIVITY)", schema = @Schema(allowableValues = {"EVENT", "CLUB_ACTIVITY"}))
            @RequestParam(required = false) String category,
            @Parameter(description = "ID sự kiện (nếu danh mục là EVENT)") @RequestParam(required = false) Integer eventId,
            @Parameter(description = "Loại tài liệu", schema = @Schema(allowableValues = {"MEETING_MINUTES", "EVENT_PLAN", "REPORT", "FINANCE", "OTHER"}))
            @RequestParam(required = false) String type,
            @RequestBody(required = false) ClubDocumentRequest body
    ) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            String finalTitle = (body != null && body.getTitle() != null) ? body.getTitle() : title;
            String finalCategory = (body != null && body.getCategory() != null) ? body.getCategory() : category;
            Integer finalEventId = (body != null && body.getEventId() != null) ? body.getEventId() : eventId;
            String finalType = (body != null && body.getDocumentType() != null) ? body.getDocumentType() : type;

            ClubDocument doc = clubDocumentService.updateDocument(id, effectiveUserId, finalTitle, finalCategory, finalEventId, finalType);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cập nhật danh mục của tài liệu (EVENT hoặc CLUB_ACTIVITY) - tương tự cấu trúc bên Google Sheet.
     * PUT /api/documents/{id}/category
     */
    @Operation(summary = "Cập nhật danh mục tài liệu (chuyển đổi EVENT <-> CLUB_ACTIVITY)",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền cập nhật danh mục.")
    @PutMapping("/{id}/category")
    public ResponseEntity<?> updateDocumentCategory(
            @PathVariable Integer id,
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "Danh mục mới: EVENT hoặc CLUB_ACTIVITY", schema = @Schema(allowableValues = {"EVENT", "CLUB_ACTIVITY"}))
            @RequestParam String category,
            @Parameter(description = "ID sự kiện (nếu category là EVENT)") @RequestParam(required = false) Integer eventId
    ) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            ClubDocument doc = clubDocumentService.updateDocumentCategory(id, effectiveUserId, category, eventId);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cập nhật tiêu đề tài liệu - tương tự cấu trúc bên Google Sheet.
     * PUT /api/documents/{id}/title
     */
    @Operation(summary = "Cập nhật tiêu đề tài liệu",
               description = "Chỉ PRESIDENT hoặc TREASURER mới có quyền cập nhật tiêu đề.")
    @PutMapping("/{id}/title")
    public ResponseEntity<?> updateDocumentTitle(
            @PathVariable Integer id,
            @Parameter(description = "ID người dùng (nếu để trống sẽ tự lấy từ token)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "Tiêu đề mới của tài liệu") @RequestParam String title
    ) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            ClubDocument doc = clubDocumentService.updateDocumentTitle(id, effectiveUserId, title);
            return ResponseEntity.ok(mapToResponse(doc));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
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
