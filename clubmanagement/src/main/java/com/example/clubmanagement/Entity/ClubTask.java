package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.SyncStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "club_tasks")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter @Setter
public class ClubTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer taskId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private ClubEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_user_id")
    private User assignedUser;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String status; //"TODO", "DOING", "DONE
    private LocalDateTime dueDate;
    private String department; //"ban ky thuat", "ban truyen thong"
    private boolean isFinanceRelated;
    private boolean isEventCritical;
    private String trelloBoardId;
    private String trelloListId;
    private String trelloCardId;
    private String trelloCardUrl;

    @Enumerated(EnumType.STRING)
    private SyncStatus syncStatus;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
