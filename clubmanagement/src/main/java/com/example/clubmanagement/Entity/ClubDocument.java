package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.DocumentType;
import com.example.clubmanagement.Enum.SyncStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "club_document")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private ClubEvent event;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private DocumentType documentType;

    @Column(name = "google_document_id", unique = true)
    private String googleDocumentId;

    @Column(name = "document_url", length = 1024)
    private String documentUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus;

    @Column(name = "content_summary", columnDefinition = "TEXT")
    private String contentSummary;

    @Column(name = "local_content", columnDefinition = "TEXT")
    private String localContent;

    // Webhook fields
    @Column(name = "webhook_channel_id")
    private String webhookChannelId;

    @Column(name = "webhook_resource_id")
    private String webhookResourceId;

    @Column(name = "webhook_expiration")
    private LocalDateTime webhookExpiration;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (syncStatus == null) {
            syncStatus = SyncStatus.PENDING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
