package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.*;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.ClubDocumentRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ClubDocumentService {

    private final ClubDocumentRepository clubDocumentRepository;
    private final DocumentRevisionRepository documentRevisionRepository;
    private final ClubRepository clubRepository;
    private final ClubEventRepository clubEventRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final GoogleAccountRepository googleAccountRepository;
    private final GoogleDocumentService googleDocumentService;

    public ClubDocumentService(ClubDocumentRepository clubDocumentRepository,
                               DocumentRevisionRepository documentRevisionRepository,
                               ClubRepository clubRepository,
                               ClubEventRepository clubEventRepository,
                               UserRepository userRepository,
                               ClubMemberRepository clubMemberRepository,
                               GoogleAccountRepository googleAccountRepository,
                               GoogleDocumentService googleDocumentService) {
        this.clubDocumentRepository = clubDocumentRepository;
        this.documentRevisionRepository = documentRevisionRepository;
        this.clubRepository = clubRepository;
        this.clubEventRepository = clubEventRepository;
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.googleAccountRepository = googleAccountRepository;
        this.googleDocumentService = googleDocumentService;
    }

    /**
     * Kiểm tra quyền ghi: Chỉ PRESIDENT hoặc TREASURER của CLB mới được thao tác.
     */
    private void checkWritePermission(Integer clubId, Integer userId) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của Câu lạc bộ này!"));

        if (member.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new RuntimeException("Tài khoản thành viên CLB của bạn chưa hoạt động!");
        }

        ClubMemberRole role = member.getRole();
        if (role != ClubMemberRole.PRESIDENT && role != ClubMemberRole.TREASURER) {
            throw new RuntimeException("Chỉ Chủ nhiệm hoặc Thủ quỹ mới có quyền quản lý tài liệu!");
        }
    }

    /**
     * Kiểm tra quyền đọc: Thành viên ACTIVE của CLB.
     */
    private void checkReadPermission(Integer clubId, Integer userId) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không có quyền xem tài liệu của Câu lạc bộ này!"));

        if (member.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new RuntimeException("Tài khoản thành viên CLB của bạn chưa hoạt động!");
        }
    }

    @Transactional
    public ClubDocument createDocument(ClubDocumentRequest request, Integer userId) throws Exception {
        Club club = clubRepository.findById(request.getClubId())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        // 1. Phân quyền nội bộ CLB
        checkWritePermission(club.getId(), userId);

        ClubEvent event = null;
        if (request.getEventId() != null) {
            event = clubEventRepository.findById(request.getEventId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy Sự kiện!"));
        }

        // 2. Tìm tài khoản Google liên kết
        GoogleAccount googleAccount = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new RuntimeException("Vui lòng liên kết tài khoản Google trước khi tạo tài liệu!"));

        // 3. Tạo record DB local (trạng thái Pending)
        ClubDocument document = ClubDocument.builder()
                .club(club)
                .event(event)
                .title(request.getTitle())
                .documentType(DocumentType.valueOf(request.getDocumentType().toUpperCase()))
                .createdBy(creator)
                .syncStatus(SyncStatus.PENDING)
                .build();
        document = clubDocumentRepository.save(document);

        try {
            // 4. Tạo file Google Docs (quyền chỉnh sửa mặc định thuộc về GoogleAccount của chủ sở hữu)
            Map<String, String> googleResult = googleDocumentService.createDocument(document.getTitle(), googleAccount);
            String docId = googleResult.get("documentId");
            document.setGoogleDocumentId(docId);
            document.setDocumentUrl(googleResult.get("documentUrl"));

            // 5. Đăng ký Webhook watch thay đổi
            Map<String, Object> watchResult = googleDocumentService.watchDocumentChanges(docId, googleAccount);
            document.setWebhookChannelId((String) watchResult.get("channelId"));
            document.setWebhookResourceId((String) watchResult.get("resourceId"));
            
            long expEpoch = (Long) watchResult.get("expiration");
            document.setWebhookExpiration(LocalDateTime.ofInstant(Instant.ofEpochMilli(expEpoch), ZoneId.systemDefault()));
            document.setSyncStatus(SyncStatus.SYNCED);

            // Lưu nội dung trống ban đầu làm revision 1
            DocumentRevision initialRevision = DocumentRevision.builder()
                    .clubDocument(document)
                    .content("")
                    .version(1)
                    .syncedAt(LocalDateTime.now())
                    .build();
            documentRevisionRepository.save(initialRevision);

        } catch (Exception e) {
            document.setSyncStatus(SyncStatus.FAILED);
            clubDocumentRepository.save(document);
            throw new RuntimeException("Đồng bộ Google Docs thất bại: " + e.getMessage(), e);
        }

        return clubDocumentRepository.save(document);
    }

    /**
     * Đồng bộ thủ công hoặc tự động cập nhật nội dung từ Google Docs về DB.
     */
    @Transactional
    public ClubDocument syncDocumentContent(Integer documentId, Integer userId) throws Exception {
        ClubDocument document = clubDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu!"));

        checkReadPermission(document.getClub().getId(), userId);

        GoogleAccount googleAccount = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(document.getCreatedBy().getUserId())
                .orElseThrow(() -> new RuntimeException("Tài khoản người tạo chưa liên kết Google hoặc token đã bị thu hồi!"));

        try {
            String newText = googleDocumentService.fetchDocumentText(document.getGoogleDocumentId(), googleAccount);
            
            // Cập nhật localContent và contentSummary
            document.setLocalContent(newText);
            
            String summary = newText.length() > 500 ? newText.substring(0, 500) + "..." : newText;
            document.setContentSummary(summary);
            document.setSyncStatus(SyncStatus.SYNCED);
            
            // Xử lý tạo revision mới nếu nội dung thay đổi
            Optional<DocumentRevision> lastRevOpt = documentRevisionRepository.findFirstByClubDocumentIdOrderByVersionDesc(documentId);
            int nextVersion = 1;
            boolean shouldSaveRevision = true;

            if (lastRevOpt.isPresent()) {
                DocumentRevision lastRev = lastRevOpt.get();
                nextVersion = lastRev.getVersion() + 1;
                if (newText.equals(lastRev.getContent())) {
                    shouldSaveRevision = false; // Không đổi, không cần thêm version mới
                }
            }

            if (shouldSaveRevision) {
                DocumentRevision nextRev = DocumentRevision.builder()
                        .clubDocument(document)
                        .content(newText)
                        .version(nextVersion)
                        .syncedAt(LocalDateTime.now())
                        .build();
                documentRevisionRepository.save(nextRev);
            }

        } catch (Exception e) {
            document.setSyncStatus(SyncStatus.FAILED);
            clubDocumentRepository.save(document);
            throw e;
        }

        return clubDocumentRepository.save(document);
    }

    /**
     * Xử lý webhook từ Google Drive.
     */
    @Transactional
    public void syncDocumentByChannel(String channelId, String resourceId) {
        Optional<ClubDocument> docOpt = clubDocumentRepository.findByWebhookChannelIdAndWebhookResourceId(channelId, resourceId);
        if (docOpt.isPresent()) {
            ClubDocument document = docOpt.get();
            try {
                // Sử dụng tài khoản của người tạo để tiến hành đồng bộ
                syncDocumentContent(document.getId(), document.getCreatedBy().getUserId());
            } catch (Exception e) {
                System.err.println("Webhook sync error for document " + document.getId() + ": " + e.getMessage());
            }
        }
    }

    public List<ClubDocument> getDocumentsByClub(Integer clubId, Integer userId) {
        checkReadPermission(clubId, userId);
        return clubDocumentRepository.findByClubId(clubId);
    }

    public List<ClubDocument> getDocumentsByClubFiltered(Integer clubId, String search, String type, String sortBy, String sortDir, Integer userId) {
        checkReadPermission(clubId, userId);

        DocumentType docType = null;
        if (type != null && !type.trim().isEmpty()) {
            try {
                docType = DocumentType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Bỏ qua nếu type không hợp lệ
            }
        }

        Sort sort = Sort.unsorted();
        if (sortBy != null && !sortBy.trim().isEmpty()) {
            Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
            if ("title".equalsIgnoreCase(sortBy)) {
                sort = Sort.by(direction, "title");
            } else if ("date".equalsIgnoreCase(sortBy)) {
                sort = Sort.by(direction, "createdAt");
            }
        }

        String searchPattern = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        return clubDocumentRepository.findClubDocumentsFiltered(clubId, searchPattern, docType, sort);
    }

    @Transactional
    public void shareDocument(Integer documentId, String role, Integer userId) throws Exception {
        ClubDocument doc = clubDocumentRepository.findById(documentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu!"));

        // Phân quyền: chỉ PRESIDENT hoặc TREASURER của CLB mới có quyền chia sẻ tài liệu
        checkWritePermission(doc.getClub().getId(), userId);

        // Lấy tài khoản Google của người tạo tài liệu (chủ sở hữu file) để thực hiện cuộc gọi API chia sẻ
        GoogleAccount googleAccount = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(doc.getCreatedBy().getUserId())
                .orElseThrow(() -> new RuntimeException("Tài khoản người tạo chưa liên kết Google hoặc token đã bị thu hồi!"));

        String targetRole = (role != null && !role.trim().isEmpty()) ? role.trim() : "commenter";
        googleDocumentService.shareDocument(doc.getGoogleDocumentId(), targetRole, googleAccount);
    }

    public List<ClubDocument> getDocumentsByEvent(Integer eventId, Integer userId) {
        ClubEvent event = clubEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Sự kiện!"));
        checkReadPermission(event.getClub().getId(), userId);
        return clubDocumentRepository.findByEventId(eventId);
    }

    public ClubDocument getDocumentById(Integer id, Integer userId) {
        ClubDocument doc = clubDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu!"));
        checkReadPermission(doc.getClub().getId(), userId);
        return doc;
    }

    public List<DocumentRevision> getDocumentRevisions(Integer id, Integer userId) {
        ClubDocument doc = clubDocumentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài liệu!"));
        checkReadPermission(doc.getClub().getId(), userId);
        return documentRevisionRepository.findByClubDocumentIdOrderByVersionDesc(id);
    }
}
