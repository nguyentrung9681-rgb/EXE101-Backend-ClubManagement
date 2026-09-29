package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.MessageType;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "chat_message", indexes = {
    @Index(name = "idx_chat_message_dept_created", columnList = "club_id, department_id, created_at DESC, id DESC"),
    @Index(name = "idx_chat_message_club_created", columnList = "club_id, created_at DESC, id DESC"),
    @Index(name = "idx_chat_message_sender", columnList = "sender_id"),
    @Index(name = "idx_chat_message_retention", columnList = "created_at, is_pinned, is_deleted")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private MessageType messageType;

    @Column(name = "is_pinned", nullable = false)
    private Boolean isPinned;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatAttachment> attachments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (isPinned == null) {
            isPinned = false;
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
        if (messageType == null) {
            messageType = MessageType.TEXT;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
