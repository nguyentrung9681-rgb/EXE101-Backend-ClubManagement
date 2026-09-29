package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ChatMessage;
import com.example.clubmanagement.Entity.ChatMessageArchive;
import com.example.clubmanagement.Repository.ChatMessageArchiveRepository;
import com.example.clubmanagement.Repository.ChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ChatArchivingService {

    private static final Logger log = LoggerFactory.getLogger(ChatArchivingService.class);

    private final ChatMessageRepository chatMessageRepository;
    private final ChatMessageArchiveRepository chatMessageArchiveRepository;

    public ChatArchivingService(ChatMessageRepository chatMessageRepository,
                                 ChatMessageArchiveRepository chatMessageArchiveRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatMessageArchiveRepository = chatMessageArchiveRepository;
    }

    /**
     * Chạy định kỳ 3:00 sáng hàng ngày để chuyển tin nhắn cũ (>= 90 ngày) sang bảng archive
     * ngoại trừ các tin nhắn được ghim (is_pinned = true)
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void archiveOldMessagesScheduled() {
        log.info("[CHAT ARCHIVE JOB] Đang bắt đầu dọn dẹp và lưu trữ tin nhắn cũ...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(90);
        int totalArchived = 0;
        int batchSize = 500;

        while (true) {
            Pageable pageable = PageRequest.of(0, batchSize);
            List<ChatMessage> oldMessages = chatMessageRepository.findMessagesToArchive(cutoffDate, pageable);

            if (oldMessages.isEmpty()) {
                break;
            }

            List<ChatMessageArchive> archives = new ArrayList<>();
            for (ChatMessage msg : oldMessages) {
                archives.add(ChatMessageArchive.builder()
                        .id(msg.getId())
                        .clubId(msg.getClub().getId())
                        .departmentId(msg.getDepartment() != null ? msg.getDepartment().getId() : null)
                        .senderId(msg.getSender().getUserId())
                        .content(msg.getContent())
                        .messageType(msg.getMessageType())
                        .isPinned(msg.getIsPinned())
                        .createdAt(msg.getCreatedAt())
                        .archivedAt(LocalDateTime.now())
                        .build());
            }

            chatMessageArchiveRepository.saveAll(archives);
            chatMessageRepository.deleteAll(oldMessages);

            totalArchived += oldMessages.size();
            log.info("[CHAT ARCHIVE JOB] Đã lưu trữ {} tin nhắn cũ.", totalArchived);
        }

        log.info("[CHAT ARCHIVE JOB] Hoàn tất dọn dẹp tin nhắn! Tổng số tin nhắn đã chuyển qua archive: {}", totalArchived);
    }

    /**
     * Chạy định kỳ 3:30 sáng hàng ngày để xóa hẳn các tin nhắn bị soft-delete đã quá 30 ngày
     */
    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void purgeSoftDeletedMessagesScheduled() {
        log.info("[CHAT CLEANUP JOB] Đang quét dọn các tin nhắn bị xóa mềm quá 30 ngày...");

        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
        int totalDeleted = 0;
        int batchSize = 500;

        while (true) {
            Pageable pageable = PageRequest.of(0, batchSize);
            List<ChatMessage> deletedMessages = chatMessageRepository.findSoftDeletedMessagesToDelete(cutoffDate, pageable);

            if (deletedMessages.isEmpty()) {
                break;
            }

            chatMessageRepository.deleteAll(deletedMessages);
            totalDeleted += deletedMessages.size();
        }

        log.info("[CHAT CLEANUP JOB] Hoàn tất xóa vĩnh viễn {} tin nhắn bị xóa mềm.", totalDeleted);
    }
}
