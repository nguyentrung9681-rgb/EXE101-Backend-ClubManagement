package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.MessageType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message_archive", indexes = {
    @Index(name = "idx_chat_message_archive_dept", columnList = "club_id, department_id, created_at DESC")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessageArchive {
    @Id
    private Long id; // Giữ nguyên ID từ chat_message chính

    @Column(name = "club_id", nullable = false)
    private Integer clubId;

    @Column(name = "department_id")
    private Integer departmentId;

    @Column(name = "sender_id", nullable = false)
    private Integer senderId;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "archived_at", nullable = false)
    private LocalDateTime archivedAt;

    @PrePersist
    protected void onArchive() {
        if (archivedAt == null) {
            archivedAt = LocalDateTime.now();
        }
    }
}
