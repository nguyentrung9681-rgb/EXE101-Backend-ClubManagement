package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Service.DepartmentService;
import com.example.clubmanagement.dto.DepartmentRequest;
import com.example.clubmanagement.dto.DepartmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs/{clubId}/departments")
public class DepartmentController {

    private final DepartmentService departmentService;

    public DepartmentController(DepartmentService departmentService) {
        this.departmentService = departmentService;
    }

    /**
     * Tạo phòng ban mới. Chỉ chủ nhiệm mới có quyền.
     * POST /api/clubs/{clubId}/departments?requesterUserId={id}
     */
    @PostMapping
    public ResponseEntity<?> createDepartment(@PathVariable Integer clubId,
                                               @RequestBody DepartmentRequest request,
                                               @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            DepartmentResponse response = departmentService.createDepartment(clubId, request, effectiveUserId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách phòng ban trong câu lạc bộ. Tất cả thành viên trong CLB đều xem được.
     * GET /api/clubs/{clubId}/departments?requesterUserId={id}
     */
    @GetMapping
    public ResponseEntity<?> getDepartments(@PathVariable Integer clubId,
                                            @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            List<DepartmentResponse> list = departmentService.getDepartments(clubId, effectiveUserId);
            return ResponseEntity.ok(list);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cập nhật thông tin phòng ban. Chỉ chủ nhiệm mới có quyền.
     * PUT /api/clubs/{clubId}/departments/{departmentId}?requesterUserId={id}
     */
    @PutMapping("/{departmentId}")
    public ResponseEntity<?> updateDepartment(@PathVariable Integer clubId,
                                               @PathVariable Integer departmentId,
                                               @RequestBody DepartmentRequest request,
                                               @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            DepartmentResponse response = departmentService.updateDepartment(clubId, departmentId, request, effectiveUserId);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Kiểm tra quyền truy cập không gian chat phòng ban.
     * GET /api/clubs/{clubId}/departments/{departmentId}/chat-access?userId={id}
     */
    @GetMapping("/{departmentId}/chat-access")
    public ResponseEntity<?> checkChatAccess(@PathVariable Integer clubId,
                                             @PathVariable Integer departmentId,
                                             @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            boolean hasAccess = departmentService.checkChatAccess(clubId, departmentId, effectiveUserId);
            Map<String, Boolean> result = new HashMap<>();
            result.put("hasAccess", hasAccess);
            return ResponseEntity.ok(result);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
