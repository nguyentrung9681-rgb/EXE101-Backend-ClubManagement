package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.SyncSource;
import com.example.clubmanagement.Enum.SyncStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "event_google_sync")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class EventGoogleSync {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_event_id", nullable = false, unique = true)
    private ClubEvent clubEvent;

    @Column(name = "google_calendar_id")
    private String googleCalendarId;

    @Column(name = "google_event_id")
    private String googleEventId;

    @Column(name = "google_event_link", length = 1024)
    private String googleEventLink;

    @Column(name = "google_etag")
    private String googleEtag;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_status", nullable = false)
    private SyncStatus syncStatus; // PENDING, SYNCED, FAILED

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_source", nullable = false)
    private SyncSource syncSource; // LOCAL, GOOGLE

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "last_sync_error", columnDefinition = "TEXT")
    private String lastSyncError;

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
        if (syncSource == null) {
            syncSource = SyncSource.LOCAL;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
