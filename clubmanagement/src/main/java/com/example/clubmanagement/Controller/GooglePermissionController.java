package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.GooglePermissionService;
import com.example.clubmanagement.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller quản lý tách biệt trao/thu hồi quyền thao tác Google Sheet và Google Form theo CLB dành cho Chủ club.
 */
@Tag(name = "Google Sheet & Form Permissions", description = "Quản lý riêng biệt trao quyền và thu hồi quyền Google Sheet & Form trong CLB")
@RestController
@RequestMapping("/api/google/permissions")
public class GooglePermissionController {

    private final GooglePermissionService googlePermissionService;

    public GooglePermissionController(GooglePermissionService googlePermissionService) {
        this.googlePermissionService = googlePermissionService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. GOOGLE SHEET PERMISSIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Trao quyền thao tác GOOGLE SHEET cho thành viên trong CLB",
               description = "Chỉ chủ nhiệm (PRESIDENT) mới có quyền trao quyền Sheet (tạo, xóa, sửa tên, sửa dữ liệu, sửa phân loại).")
    @PostMapping("/sheets/grant")
    public ResponseEntity<?> grantSheetPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID người dùng được trao quyền") @RequestParam Integer targetUserId,
            @RequestBody GoogleSheetPermissionRequest request) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.grantSheetPermissions(effectiveUserId, clubId, targetUserId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Trao quyền thao tác Google Sheet thành công!",
                    "permission", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Thu hồi quyền thao tác GOOGLE SHEET từ thành viên trong CLB",
               description = "Chỉ chủ nhiệm (PRESIDENT) mới có quyền thu hồi quyền Sheet.")
    @PostMapping("/sheets/revoke")
    public ResponseEntity<?> revokeSheetPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID người dùng bị thu hồi quyền") @RequestParam Integer targetUserId,
            @RequestBody(required = false) GoogleSheetPermissionRequest request) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.revokeSheetPermissions(effectiveUserId, clubId, targetUserId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Thu hồi quyền thao tác Google Sheet thành công!",
                    "permission", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. GOOGLE FORM PERMISSIONS
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Trao quyền thao tác GOOGLE FORM cho thành viên trong CLB",
               description = "Chỉ chủ nhiệm (PRESIDENT) mới có quyền trao quyền Form (tạo, xóa, sửa tên, sửa dữ liệu/câu hỏi, sửa phân loại).")
    @PostMapping("/forms/grant")
    public ResponseEntity<?> grantFormPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID người dùng được trao quyền") @RequestParam Integer targetUserId,
            @RequestBody GoogleFormPermissionRequest request) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.grantFormPermissions(effectiveUserId, clubId, targetUserId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Trao quyền thao tác Google Form thành công!",
                    "permission", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Thu hồi quyền thao tác GOOGLE FORM từ thành viên trong CLB",
               description = "Chỉ chủ nhiệm (PRESIDENT) mới có quyền thu hồi quyền Form.")
    @PostMapping("/forms/revoke")
    public ResponseEntity<?> revokeFormPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID người dùng bị thu hồi quyền") @RequestParam Integer targetUserId,
            @RequestBody(required = false) GoogleFormPermissionRequest request) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.revokeFormPermissions(effectiveUserId, clubId, targetUserId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Thu hồi quyền thao tác Google Form thành công!",
                    "permission", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. GENERAL PERMISSIONS (Sheet + Form)
    // ─────────────────────────────────────────────────────────────────────────

    @Operation(summary = "Trao quyền tổng hợp (Google Sheet & Form) cho thành viên trong CLB",
               description = "Chỉ chủ nhiệm (PRESIDENT) mới có quyền thực hiện.")
    @PostMapping("/grant")
    public ResponseEntity<?> grantPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID người dùng được trao quyền") @RequestParam Integer targetUserId,
            @RequestBody GooglePermissionRequest request) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.grantPermissions(effectiveUserId, clubId, targetUserId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Trao quyền thao tác Google Sheet/Form thành công!",
                    "permission", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Thu hồi quyền tổng hợp (Google Sheet & Form) từ thành viên trong CLB",
               description = "Chỉ chủ nhiệm (PRESIDENT) mới có quyền thực hiện.")
    @PostMapping("/revoke")
    public ResponseEntity<?> revokePermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @Parameter(description = "ID CLB") @RequestParam Integer clubId,
            @Parameter(description = "ID người dùng bị thu hồi quyền") @RequestParam Integer targetUserId,
            @RequestBody(required = false) GooglePermissionRequest request) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.revokePermissions(effectiveUserId, clubId, targetUserId, request);
            return ResponseEntity.ok(Map.of(
                    "message", "Thu hồi quyền thao tác Google Sheet/Form thành công!",
                    "permission", response
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Lấy danh sách tất cả thành viên được trao quyền Google Sheet / Form trong CLB",
               description = "Trả về trạng thái các quyền Google Sheet & Form đã được trao cho các thành viên trong CLB.")
    @GetMapping("/club/{clubId}")
    public ResponseEntity<?> getClubPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @PathVariable Integer clubId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<GooglePermissionResponse> list = googlePermissionService.getClubPermissions(effectiveUserId, clubId);
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @Operation(summary = "Xem quyền Google Sheet / Form của một thành viên cụ thể trong CLB",
               description = "Trả về chi tiết trạng thái các quyền Sheet & Form của thành viên chỉ định.")
    @GetMapping("/club/{clubId}/user/{targetUserId}")
    public ResponseEntity<?> getUserPermissions(
            @Parameter(description = "ID người thực hiện (tự động lấy từ token nếu trống)") @RequestParam(required = false) Integer userId,
            @PathVariable Integer clubId,
            @PathVariable Integer targetUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            GooglePermissionResponse response = googlePermissionService.getUserPermissions(effectiveUserId, clubId, targetUserId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }
}
