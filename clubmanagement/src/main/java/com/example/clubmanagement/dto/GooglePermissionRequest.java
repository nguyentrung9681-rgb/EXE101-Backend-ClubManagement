package com.example.clubmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GooglePermissionRequest {

    // ────────────── Quyền Google Sheet ──────────────
    @Schema(description = "Quyền tạo mới Google Sheet", example = "true")
    private Boolean canCreateSheet;

    @Schema(description = "Quyền xóa Google Sheet", example = "true")
    private Boolean canDeleteSheet;

    @Schema(description = "Quyền chỉnh sửa tên / tiêu đề Google Sheet", example = "true")
    private Boolean canEditSheetTitle;

    @Schema(description = "Quyền chỉnh sửa dữ liệu Google Sheet", example = "true")
    private Boolean canEditSheetData;

    @Schema(description = "Quyền chỉnh sửa phân loại Google Sheet", example = "true")
    private Boolean canEditSheetType;

    // ────────────── Quyền Google Form ──────────────
    @Schema(description = "Quyền tạo mới Google Form", example = "true")
    private Boolean canCreateForm;

    @Schema(description = "Quyền xóa Google Form", example = "true")
    private Boolean canDeleteForm;

    @Schema(description = "Quyền chỉnh sửa tên / tiêu đề Google Form", example = "true")
    private Boolean canEditFormTitle;

    @Schema(description = "Quyền chỉnh sửa dữ liệu / câu hỏi Google Form", example = "true")
    private Boolean canEditFormData;

    @Schema(description = "Quyền chỉnh sửa phân loại Google Form", example = "true")
    private Boolean canEditFormType;
}
