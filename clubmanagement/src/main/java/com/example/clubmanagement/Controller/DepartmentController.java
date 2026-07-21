package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.DepartmentService;
import com.example.clubmanagement.dto.DepartmentRequest;
import com.example.clubmanagement.dto.DepartmentResponse;
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
                                               @RequestParam Integer requesterUserId) {
        try {
            DepartmentResponse response = departmentService.createDepartment(clubId, request, requesterUserId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách phòng ban trong câu lạc bộ. Tất cả thành viên trong CLB đều xem được.
     * GET /api/clubs/{clubId}/departments?requesterUserId={id}
     */
    @GetMapping
    public ResponseEntity<?> getDepartments(@PathVariable Integer clubId,
                                            @RequestParam Integer requesterUserId) {
        try {
            List<DepartmentResponse> list = departmentService.getDepartments(clubId, requesterUserId);
            return ResponseEntity.ok(list);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
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
                                               @RequestParam Integer requesterUserId) {
        try {
            DepartmentResponse response = departmentService.updateDepartment(clubId, departmentId, request, requesterUserId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Kiểm tra quyền truy cập không gian chat phòng ban.
     * GET /api/clubs/{clubId}/departments/{departmentId}/chat-access?userId={id}
     */
    @GetMapping("/{departmentId}/chat-access")
    public ResponseEntity<?> checkChatAccess(@PathVariable Integer clubId,
                                             @PathVariable Integer departmentId,
                                             @RequestParam Integer userId) {
        try {
            boolean hasAccess = departmentService.checkChatAccess(clubId, departmentId, userId);
            Map<String, Boolean> result = new HashMap<>();
            result.put("hasAccess", hasAccess);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
