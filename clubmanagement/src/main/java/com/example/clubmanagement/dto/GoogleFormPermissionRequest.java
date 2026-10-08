package com.example.clubmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class GoogleFormPermissionRequest {

    @Schema(description = "Quyền tạo mới Google Form", example = "true")
    private Boolean canCreate;

    @Schema(description = "Quyền xóa Google Form", example = "true")
    private Boolean canDelete;

    @Schema(description = "Quyền chỉnh sửa tên / tiêu đề Google Form", example = "true")
    private Boolean canEditTitle;

    @Schema(description = "Quyền chỉnh sửa dữ liệu / cấu trúc câu hỏi Google Form", example = "true")
    private Boolean canEditData;

    @Schema(description = "Quyền chỉnh sửa phân loại Google Form (EVENT / CLUB_ACTIVITIES)", example = "true")
    private Boolean canEditType;
}
