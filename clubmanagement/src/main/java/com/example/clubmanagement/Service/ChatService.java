package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.MessageType;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageArchiveRepository chatMessageArchiveRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ClubRepository clubRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    private static final String UPLOAD_DIR = "uploads/chat/";

    /**
     * Tải tin nhắn của Kênh Chat PHÒNG BAN (Lazy loading Cursor Pagination)
     * GET /api/chat/clubs/{clubId}/departments/{departmentId}/messages
     */
    public MessageCursorPageResponse getDepartmentMessages(Integer clubId, Integer departmentId, Integer userId,
                                                           Integer limit, Long beforeMessageId) {
        validateDepartmentMembership(clubId, departmentId, userId);

        int fetchLimit = (limit == null || limit <= 0 || limit > 100) ? 50 : limit;
        Pageable pageable = PageRequest.of(0, fetchLimit + 1);

        List<ChatMessage> rawMessages;
        if (beforeMessageId != null && beforeMessageId > 0) {
            rawMessages = chatMessageRepository.findDepartmentMessagesBeforeId(clubId, departmentId, beforeMessageId, pageable);
        } else {
            rawMessages = chatMessageRepository.findRecentDepartmentMessages(clubId, departmentId, pageable);
        }

        return buildPageResponse(rawMessages, fetchLimit);
    }

    /**
     * Gửi tin nhắn mới vào Kênh Chat PHÒNG BAN
     * POST /api/chat/clubs/{clubId}/departments/{departmentId}/messages
     */
    @Transactional
    public ChatMessageResponse sendDepartmentMessage(Integer clubId, Integer departmentId, Integer userId,
                                                      ChatMessageRequest request) {
        validateDepartmentMembership(clubId, departmentId, userId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Phòng ban!"));
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người gửi!"));

        ChatMessage message = buildMessageEntity(club, department, sender, request);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        ChatMessageResponse response = mapToMessageResponse(savedMessage);

        // 1. Phát realtime tin nhắn mới tới Room phòng ban
        String deptTopic = String.format("/topic/clubs/%d/departments/%d", clubId, departmentId);
        simpMessagingTemplate.convertAndSend(deptTopic, response);

        // 2. Gửi notification chuông tới các thành viên khác trong phòng ban
        notifyDepartmentMembers(clubId, departmentId, sender, savedMessage);

        return response;
    }

    /**
     * Tải tin nhắn của Kênh CHUNG CLB (Lazy loading Cursor Pagination)
     * GET /api/chat/clubs/{clubId}/messages
     */
    public MessageCursorPageResponse getClubMessages(Integer clubId, Integer userId, Integer limit, Long beforeMessageId) {
        validateClubMembership(clubId, userId);

        int fetchLimit = (limit == null || limit <= 0 || limit > 100) ? 50 : limit;
        Pageable pageable = PageRequest.of(0, fetchLimit + 1);

        List<ChatMessage> rawMessages;
        if (beforeMessageId != null && beforeMessageId > 0) {
            rawMessages = chatMessageRepository.findClubMessagesBeforeId(clubId, beforeMessageId, pageable);
        } else {
            rawMessages = chatMessageRepository.findRecentClubMessages(clubId, pageable);
        }

        return buildPageResponse(rawMessages, fetchLimit);
    }

    /**
     * Gửi tin nhắn mới vào Kênh CHUNG CLB
     * POST /api/chat/clubs/{clubId}/messages
     */
    @Transactional
    public ChatMessageResponse sendClubMessage(Integer clubId, Integer userId, ChatMessageRequest request) {
        validateClubMembership(clubId, userId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
        User sender = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản người gửi!"));

        ChatMessage message = buildMessageEntity(club, null, sender, request);
        ChatMessage savedMessage = chatMessageRepository.save(message);
        ChatMessageResponse response = mapToMessageResponse(savedMessage);

        // 1. Phát realtime tin nhắn mới tới Kênh chung CLB
        String clubTopic = String.format("/topic/clubs/%d", clubId);
        simpMessagingTemplate.convertAndSend(clubTopic, response);

        // 2. Gửi notification chuông tới các thành viên khác trong CLB
        notifyClubMembers(clubId, sender, savedMessage);

        return response;
    }

    /**
     * Upload tệp tin đính kèm
     */
    public ChatAttachmentDto uploadAttachment(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("Tệp tin tải lên rỗng!");
        }

        // Giới hạn kích thước file tải lên (tối đa 10 MB)
        long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("Kích thước tệp tin vượt quá giới hạn cho phép (Tối đa 10MB)!");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename != null) {
            originalFilename = Paths.get(originalFilename).getFileName().toString();
        }

        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
        }

        // Danh sách đuôi file cấm tuyệt đối (Executable / Script / Markup / XSS risk)
        List<String> FORBIDDEN_EXTENSIONS = List.of(
                ".exe", ".sh", ".bat", ".cmd", ".html", ".htm", ".xhtml", ".shtml", ".xml", ".svg",
                ".js", ".jsp", ".jspx", ".php", ".phtml", ".php3", ".php4", ".php5", ".phps",
                ".asp", ".aspx", ".ashx", ".asmx", ".cgi", ".pl", ".py", ".rb",
                ".jar", ".war", ".ear", ".vbs", ".ps1", ".psm1", ".dll", ".so", ".dylib",
                ".sys", ".drv", ".scr", ".com", ".pif", ".application", ".gadget", ".msi", ".msp", ".hta", ".cpl", ".msc"
        );

        if (FORBIDDEN_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Loại tệp tin này không được phép tải lên vì lý do bảo mật!");
        }

        try {
            File folder = new File(UPLOAD_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String storedFileName = UUID.randomUUID() + extension;
            Path filePath = Paths.get(UPLOAD_DIR).resolve(storedFileName).normalize();
            
            // Đảm bảo không thoát khỏi thư mục UPLOAD_DIR
            if (!filePath.startsWith(Paths.get(UPLOAD_DIR).toAbsolutePath().normalize())) {
                throw new SecurityException("Đường dẫn file lưu trữ không hợp lệ!");
            }

            Files.copy(file.getInputStream(), filePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String fileUrl = "/api/chat/files/" + storedFileName;

            return ChatAttachmentDto.builder()
                    .fileUrl(fileUrl)
                    .fileName(originalFilename != null ? originalFilename : storedFileName)
                    .fileSize(file.getSize())
                    .fileType(file.getContentType())
                    .build();

        } catch (IOException e) {
            throw new RuntimeException("Lỗi lưu trữ tệp đính kèm: " + e.getMessage());
        }
    }

    /**
     * Ghim / Bỏ ghim tin nhắn
     */
    @Transactional
    public ChatMessageResponse togglePinMessage(Long messageId, Integer userId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn!"));

        // Bảo mật IDOR: Kiểm tra đúng kênh phòng ban hoặc kênh chung CLB
        if (message.getDepartment() != null) {
            validateDepartmentMembership(message.getClub().getId(), message.getDepartment().getId(), userId);
        } else {
            validateClubMembership(message.getClub().getId(), userId);
        }

        // Kiểm tra quyền ghim: Người gửi, Chủ nhiệm hoặc Trưởng ban phòng ban tương ứng
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(message.getClub().getId(), userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của câu lạc bộ này!"));

        boolean isSender = message.getSender().getUserId().equals(userId);
        boolean isPresident = member.getRole() == ClubMemberRole.PRESIDENT;
        boolean isDeptHead = member.getRole() == ClubMemberRole.DEPARTMENT_HEAD 
                && message.getDepartment() != null 
                && member.getDepartment() != null 
                && member.getDepartment().getId().equals(message.getDepartment().getId());

        if (!isSender && !isPresident && !isDeptHead) {
            throw new SecurityException("Bạn không có quyền ghim hoặc bỏ ghim tin nhắn này!");
        }

        message.setIsPinned(!Boolean.TRUE.equals(message.getIsPinned()));
        ChatMessage updated = chatMessageRepository.save(message);
        ChatMessageResponse response = mapToMessageResponse(updated);

        // Phát event cập nhật ghim tin nhắn tới Room
        String pinTopic = message.getDepartment() != null
                ? String.format("/topic/clubs/%d/departments/%d", message.getClub().getId(), message.getDepartment().getId())
                : String.format("/topic/clubs/%d", message.getClub().getId());
        simpMessagingTemplate.convertAndSend(pinTopic, response);

        return response;
    }

    /**
     * Xóa mềm tin nhắn (Cho phép chính người gửi hoặc Ban quản trị/Trưởng ban kiểm duyệt)
     */
    @Transactional
    public void deleteMessage(Long messageId, Integer userId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn!"));

        validateClubMembership(message.getClub().getId(), userId);

        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(message.getClub().getId(), userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của câu lạc bộ này!"));

        boolean isSender = message.getSender().getUserId().equals(userId);
        boolean isPresident = member.getRole() == ClubMemberRole.PRESIDENT;
        boolean isDeptHead = member.getRole() == ClubMemberRole.DEPARTMENT_HEAD 
                && message.getDepartment() != null 
                && member.getDepartment() != null 
                && member.getDepartment().getId().equals(message.getDepartment().getId());

        if (!isSender && !isPresident && !isDeptHead) {
            throw new SecurityException("Bạn không có quyền xóa tin nhắn này!");
        }

        message.setIsDeleted(true);
        message.setUpdatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);

        // Phát event xóa tin nhắn tới Room
        String deleteTopic = message.getDepartment() != null
                ? String.format("/topic/clubs/%d/departments/%d", message.getClub().getId(), message.getDepartment().getId())
                : String.format("/topic/clubs/%d", message.getClub().getId());
        NotificationDto deleteEvent = NotificationDto.builder()
                .type("MESSAGE_DELETED")
                .clubId(message.getClub().getId())
                .departmentId(message.getDepartment() != null ? message.getDepartment().getId() : null)
                .messageId(messageId)
                .build();
        simpMessagingTemplate.convertAndSend(deleteTopic, deleteEvent);
    }

    private void notifyDepartmentMembers(Integer clubId, Integer departmentId, User sender, ChatMessage message) {
        try {
            String snippet = message.getContent() != null ? message.getContent() : "[Tệp đính kèm]";
            for (Object[] row : clubMemberRepository.findUserIdAndUsernameByDepartmentIdAndStatus(
                    departmentId, ClubMemberStatus.ACTIVE)) {
                Integer userId = (Integer) row[0];
                String username = (String) row[1];
                if (username == null || userId.equals(sender.getUserId())) {
                    continue;
                }
                NotificationDto notification = NotificationDto.builder()
                        .type("NEW_CHAT_MESSAGE")
                        .title("Tin nhắn mới từ " + sender.getFullName())
                        .snippet(snippet)
                        .clubId(clubId)
                        .departmentId(departmentId)
                        .messageId(message.getId())
                        .senderId(sender.getUserId())
                        .senderName(sender.getFullName())
                        .senderAvatar(sender.getAvatarUrl())
                        .createdAt(message.getCreatedAt())
                        .build();

                simpMessagingTemplate.convertAndSendToUser(username, "/queue/notifications", notification);
                simpMessagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", notification);
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo tin nhắn phòng ban: {}", e.getMessage());
        }
    }

    private void notifyClubMembers(Integer clubId, User sender, ChatMessage message) {
        try {
            String snippet = message.getContent() != null ? message.getContent() : "[Tệp đính kèm]";
            String clubName = message.getClub() != null ? message.getClub().getName() : null;
            String title = (clubName != null ? clubName + " - " : "") + sender.getFullName();
            for (Object[] row : clubMemberRepository.findUserIdAndUsernameByClubIdAndStatus(
                    clubId, ClubMemberStatus.ACTIVE)) {
                Integer userId = (Integer) row[0];
                String username = (String) row[1];
                if (username == null || userId.equals(sender.getUserId())) {
                    continue;
                }
                NotificationDto notification = NotificationDto.builder()
                        .type("NEW_CHAT_MESSAGE")
                        .title(title)
                        .snippet(snippet)
                        .clubId(clubId)
                        .messageId(message.getId())
                        .senderId(sender.getUserId())
                        .senderName(sender.getFullName())
                        .senderAvatar(sender.getAvatarUrl())
                        .createdAt(message.getCreatedAt())
                        .build();

                simpMessagingTemplate.convertAndSendToUser(username, "/queue/notifications", notification);
                simpMessagingTemplate.convertAndSend("/topic/users/" + userId + "/notifications", notification);
            }
        } catch (Exception e) {
            log.error("Lỗi khi gửi thông báo tin nhắn CLB: {}", e.getMessage());
        }
    }

    // --- Helper Validation & Mapping Methods ---

    private void validateClubMembership(Integer clubId, Integer userId) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của câu lạc bộ này!"));
        if (member.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new RuntimeException("Tài khoản thành viên chưa hoạt động trong CLB!");
        }
    }

    private void validateDepartmentMembership(Integer clubId, Integer departmentId, Integer userId) {
        validateClubMembership(clubId, userId);
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId).orElse(null);
        if (member == null || member.getDepartment() == null || !member.getDepartment().getId().equals(departmentId)) {
            throw new RuntimeException("Bạn không thuộc phòng ban này để truy cập kênh chat!");
        }
    }

    private ChatMessage buildMessageEntity(Club club, Department department, User sender, ChatMessageRequest request) {
        ChatMessage message = ChatMessage.builder()
                .club(club)
                .department(department)
                .sender(sender)
                .content(request.getContent())
                .messageType(request.getMessageType() != null ? request.getMessageType() : MessageType.TEXT)
                .isPinned(Boolean.TRUE.equals(request.getIsPinned()))
                .isDeleted(false)
                .build();

        if (request.getAttachments() != null && !request.getAttachments().isEmpty()) {
            List<ChatAttachment> attachments = new ArrayList<>();
            for (ChatAttachmentDto dto : request.getAttachments()) {
                ChatAttachment attachment = ChatAttachment.builder()
                        .message(message)
                        .fileUrl(dto.getFileUrl())
                        .fileName(dto.getFileName())
                        .fileSize(dto.getFileSize())
                        .fileType(dto.getFileType())
                        .build();
                attachments.add(attachment);
            }
            message.setAttachments(attachments);
            if (message.getMessageType() == MessageType.TEXT) {
                message.setMessageType(MessageType.FILE);
            }
        }
        return message;
    }

    private MessageCursorPageResponse buildPageResponse(List<ChatMessage> rawMessages, int fetchLimit) {
        boolean hasMore = rawMessages.size() > fetchLimit;
        List<ChatMessage> pageMessages = hasMore ? rawMessages.subList(0, fetchLimit) : rawMessages;

        Long nextBeforeMessageId = null;
        if (!pageMessages.isEmpty()) {
            nextBeforeMessageId = pageMessages.get(pageMessages.size() - 1).getId();
        }

        List<ChatMessageResponse> messageResponses = pageMessages.stream()
                .map(this::mapToMessageResponse)
                .collect(Collectors.toList());

        return MessageCursorPageResponse.builder()
                .messages(messageResponses)
                .nextBeforeMessageId(nextBeforeMessageId)
                .hasMore(hasMore)
                .limit(fetchLimit)
                .build();
    }

    private ChatMessageResponse mapToMessageResponse(ChatMessage message) {
        List<ChatAttachmentDto> attachmentDtos = new ArrayList<>();
        if (message.getAttachments() != null) {
            for (ChatAttachment att : message.getAttachments()) {
                attachmentDtos.add(ChatAttachmentDto.builder()
                        .id(att.getId())
                        .fileUrl(att.getFileUrl())
                        .fileName(att.getFileName())
                        .fileSize(att.getFileSize())
                        .fileType(att.getFileType())
                        .build());
            }
        }

        return ChatMessageResponse.builder()
                .id(message.getId())
                .clubId(message.getClub().getId())
                .departmentId(message.getDepartment() != null ? message.getDepartment().getId() : null)
                .departmentName(message.getDepartment() != null ? message.getDepartment().getName() : null)
                .senderId(message.getSender().getUserId())
                .senderName(message.getSender().getFullName())
                .senderAvatar(message.getSender().getAvatarUrl())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .isPinned(message.getIsPinned())
                .isDeleted(message.getIsDeleted())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt())
                .attachments(attachmentDtos)
                .build();
    }

    /**
     * Lấy danh sách các emoji phổ biến theo từng danh mục hỗ trợ cho trình chọn emoji (Emoji Picker).
     */
    public List<EmojiCategoryResponse> getAvailableEmojis() {
        List<EmojiCategoryResponse> categories = new ArrayList<>();

        categories.add(EmojiCategoryResponse.builder()
                .category("Biểu cảm & Gương mặt (Smileys & Emotion)")
                .emojis(List.of("😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇",
                                "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚",
                                "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🤩",
                                "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣",
                                "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
                                "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗",
                                "🤔", "🤭", "🤫", "🤥", "😶", "😐", "😑", "😬", "🙄", "😯",
                                "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐"))
                .build());

        categories.add(EmojiCategoryResponse.builder()
                .category("Cử chỉ & Bàn tay (Gestures & Hands)")
                .emojis(List.of("👋", "🤚", "🖐️", "✋", "🖖", "👌", "🤌", "🤏", "✌️", "🤞",
                                "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "☝️", "👍",
                                "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝",
                                "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿"))
                .build());

        categories.add(EmojiCategoryResponse.builder()
                .category("Trái tim & Cảm xúc (Hearts & Reactions)")
                .emojis(List.of("❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔",
                                "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "🔥",
                                "✨", "🌟", "⭐", "💥", "💯", "💢", "💬", "💭"))
                .build());

        categories.add(EmojiCategoryResponse.builder()
                .category("Chúc mừng & Hoạt động (Celebration & Activities)")
                .emojis(List.of("🎉", "🎊", "🎈", "🎂", "🎁", "🏆", "🥇", "🥈", "🥉", "🏅",
                                "🎗️", "🎯", "🚀", "📢", "📣", "🔔", "💡", "📌", "📌", "👑"))
                .build());

        return categories;
    }
}
