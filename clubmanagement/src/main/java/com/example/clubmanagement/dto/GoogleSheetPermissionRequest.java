package com.example.clubmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoogleSheetPermissionRequest {

    @Schema(description = "Quyền tạo mới Google Sheet", example = "true")
    private Boolean canCreate;

    @Schema(description = "Quyền xóa Google Sheet", example = "true")
    private Boolean canDelete;

    @Schema(description = "Quyền chỉnh sửa tên / tiêu đề Google Sheet", example = "true")
    private Boolean canEditTitle;

    @Schema(description = "Quyền chỉnh sửa dữ liệu trong Google Sheet", example = "true")
    private Boolean canEditData;

    @Schema(description = "Quyền chỉnh sửa phân loại Google Sheet (EVENT / CLUB_ACTIVITIES)", example = "true")
    private Boolean canEditType;
}
