package com.example.clubmanagement.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "club_google_permission", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"club_id", "user_id"})
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubGooglePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ────────────── Quyền Google Sheet ──────────────
    @Column(name = "can_create_sheet", nullable = false, columnDefinition = "boolean default false")
    private boolean canCreateSheet;

    @Column(name = "can_delete_sheet", nullable = false, columnDefinition = "boolean default false")
    private boolean canDeleteSheet;

    @Column(name = "can_edit_sheet_title", nullable = false, columnDefinition = "boolean default false")
    private boolean canEditSheetTitle;

    @Column(name = "can_edit_sheet_data", nullable = false, columnDefinition = "boolean default false")
    private boolean canEditSheetData;

    @Column(name = "can_edit_sheet_type", nullable = false, columnDefinition = "boolean default false")
    private boolean canEditSheetType;

    // ────────────── Quyền Google Form ──────────────
    @Column(name = "can_create_form", nullable = false, columnDefinition = "boolean default false")
    private boolean canCreateForm;

    @Column(name = "can_delete_form", nullable = false, columnDefinition = "boolean default false")
    private boolean canDeleteForm;

    @Column(name = "can_edit_form_title", nullable = false, columnDefinition = "boolean default false")
    private boolean canEditFormTitle;

    @Column(name = "can_edit_form_data", nullable = false, columnDefinition = "boolean default false")
    private boolean canEditFormData;

    @Column(name = "can_edit_form_type", nullable = false, columnDefinition = "boolean default false")
    private boolean canEditFormType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "granted_by_user_id")
    private User grantedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        this.updatedAt = LocalDateTime.now();
    }
}
