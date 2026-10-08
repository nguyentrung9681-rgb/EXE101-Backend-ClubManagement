package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GooglePermissionResponse {
    private Integer id;
    private Integer clubId;
    private Integer userId;
    private String userFullName;
    private String userEmail;

    // ────────────── Quyền Google Sheet ──────────────
    private boolean canCreateSheet;
    private boolean canDeleteSheet;
    private boolean canEditSheetTitle;
    private boolean canEditSheetData;
    private boolean canEditSheetType;

    // ────────────── Quyền Google Form ──────────────
    private boolean canCreateForm;
    private boolean canDeleteForm;
    private boolean canEditFormTitle;
    private boolean canEditFormData;
    private boolean canEditFormType;

    private Integer grantedByUserId;
    private String grantedByFullName;
    private LocalDateTime updatedAt;
}
