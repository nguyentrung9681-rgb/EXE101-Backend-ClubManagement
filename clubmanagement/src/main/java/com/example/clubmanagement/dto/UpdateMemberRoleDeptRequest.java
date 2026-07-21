package com.example.clubmanagement.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class UpdateMemberRoleDeptRequest {
    @Schema(allowableValues = {"DEPARTMENT_HEAD", "TREASURER", "MEMBER"}, description = "Vai trò mới gán cho thành viên")
    private String role;
    private Integer departmentId;
}
