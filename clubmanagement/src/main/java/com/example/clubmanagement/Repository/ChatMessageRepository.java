package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    // 1. Phân trang tin mới nhất của Phòng ban (departmentId != null)
    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender LEFT JOIN FETCH m.attachments " +
           "WHERE m.club.id = :clubId AND m.department.id = :departmentId AND m.isDeleted = false " +
           "ORDER BY m.id DESC")
    List<ChatMessage> findRecentDepartmentMessages(@Param("clubId") Integer clubId,
                                                    @Param("departmentId") Integer departmentId,
                                                    Pageable pageable);

    // 2. Phân trang cuộn lên của Phòng ban (id < beforeMessageId)
    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender LEFT JOIN FETCH m.attachments " +
           "WHERE m.club.id = :clubId AND m.department.id = :departmentId AND m.id < :beforeMessageId AND m.isDeleted = false " +
           "ORDER BY m.id DESC")
    List<ChatMessage> findDepartmentMessagesBeforeId(@Param("clubId") Integer clubId,
                                                      @Param("departmentId") Integer departmentId,
                                                      @Param("beforeMessageId") Long beforeMessageId,
                                                      Pageable pageable);

    // 3. Phân trang tin mới nhất của Kênh chung CLB (departmentId IS NULL)
    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender LEFT JOIN FETCH m.attachments " +
           "WHERE m.club.id = :clubId AND m.department IS NULL AND m.isDeleted = false " +
           "ORDER BY m.id DESC")
    List<ChatMessage> findRecentClubMessages(@Param("clubId") Integer clubId, Pageable pageable);

    // 4. Phân trang cuộn lên của Kênh chung CLB (id < beforeMessageId)
    @Query("SELECT m FROM ChatMessage m LEFT JOIN FETCH m.sender LEFT JOIN FETCH m.attachments " +
           "WHERE m.club.id = :clubId AND m.department IS NULL AND m.id < :beforeMessageId AND m.isDeleted = false " +
           "ORDER BY m.id DESC")
    List<ChatMessage> findClubMessagesBeforeId(@Param("clubId") Integer clubId,
                                                @Param("beforeMessageId") Long beforeMessageId,
                                                Pageable pageable);

    // 5. Lấy tin nhắn mới nhất để hiển thị preview
    Optional<ChatMessage> findFirstByClubIdAndDepartmentIdAndIsDeletedFalseOrderByIdDesc(Integer clubId, Integer departmentId);
    Optional<ChatMessage> findFirstByClubIdAndDepartmentIsNullAndIsDeletedFalseOrderByIdDesc(Integer clubId);

    // 6. Lấy danh sách tin nhắn cũ cần archive
    @Query("SELECT m FROM ChatMessage m WHERE m.createdAt < :cutoffDate AND m.isPinned = false")
    List<ChatMessage> findMessagesToArchive(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);

    // 7. Lấy danh sách tin nhắn đã xóa mềm quá 30 ngày để dọn dẹp vĩnh viễn
    @Query("SELECT m FROM ChatMessage m WHERE m.isDeleted = true AND m.updatedAt < :cutoffDate")
    List<ChatMessage> findSoftDeletedMessagesToDelete(@Param("cutoffDate") LocalDateTime cutoffDate, Pageable pageable);
}
