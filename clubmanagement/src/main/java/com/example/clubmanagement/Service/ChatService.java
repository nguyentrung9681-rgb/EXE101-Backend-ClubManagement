package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.MessageType;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

@Service
public class ChatService {

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageArchiveRepository chatMessageArchiveRepository;
    private final ChatAttachmentRepository chatAttachmentRepository;
    private final ClubRepository clubRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final ClubMemberRepository clubMemberRepository;

    private static final String UPLOAD_DIR = "uploads/chat/";

    public ChatService(ChatMessageRepository chatMessageRepository,
                       ChatMessageArchiveRepository chatMessageArchiveRepository,
                       ChatAttachmentRepository chatAttachmentRepository,
                       ClubRepository clubRepository,
                       DepartmentRepository departmentRepository,
                       UserRepository userRepository,
                       ClubMemberRepository clubMemberRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageArchiveRepository = chatMessageArchiveRepository;
        this.chatAttachmentRepository = chatAttachmentRepository;
        this.clubRepository = clubRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
        this.clubMemberRepository = clubMemberRepository;
    }

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
        return mapToMessageResponse(savedMessage);
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
        return mapToMessageResponse(savedMessage);
    }

    /**
     * Upload tệp tin đính kèm
     */
    public ChatAttachmentDto uploadAttachment(MultipartFile file) {
        if (file.isEmpty()) {
            throw new RuntimeException("Tệp tin tải lên rỗng!");
        }

        try {
            File folder = new File(UPLOAD_DIR);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String storedFileName = UUID.randomUUID() + extension;
            Path filePath = Paths.get(UPLOAD_DIR + storedFileName);
            Files.copy(file.getInputStream(), filePath);

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
        validateClubMembership(message.getClub().getId(), userId);

        message.setIsPinned(!Boolean.TRUE.equals(message.getIsPinned()));
        ChatMessage updated = chatMessageRepository.save(message);
        return mapToMessageResponse(updated);
    }

    /**
     * Xóa mềm tin nhắn
     */
    @Transactional
    public void deleteMessage(Long messageId, Integer userId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tin nhắn!"));

        if (!message.getSender().getUserId().equals(userId)) {
            throw new RuntimeException("Bạn chỉ có thể xóa tin nhắn của chính mình!");
        }

        message.setIsDeleted(true);
        message.setUpdatedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
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
}
