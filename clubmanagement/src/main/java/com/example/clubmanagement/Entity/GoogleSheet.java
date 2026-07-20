package com.example.clubmanagement.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = "google_sheets")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoogleSheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "spreadsheet_id", nullable = false, unique = true)
    private String spreadsheetId;

    @Column(name = "title", nullable = false)
    private String title;

    /**
     * Phân loại bắt buộc: EVENT (Sự kiện) hoặc CLUB_ACTIVITIES (Hoạt động CLB).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private SheetFormType type;

    @Column(name = "spreadsheet_url", nullable = false, length = 1024)
    private String spreadsheetUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * CLB sở hữu file Google Sheet này.
     * Chỉ thành viên ACTIVE của CLB này mới được xem/tạo/xóa.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
