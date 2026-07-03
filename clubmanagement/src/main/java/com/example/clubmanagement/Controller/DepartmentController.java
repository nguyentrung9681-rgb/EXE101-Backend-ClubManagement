package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.Department;
import com.example.clubmanagement.Entity.DepartmentMember;
import com.example.clubmanagement.Enum.DepartmentRole;
import com.example.clubmanagement.Service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/departments")
public class DepartmentController {
    @Autowired
    private DepartmentService departmentService;

    // 1. Tạo phòng ban mới (Chỉ Club Manager được quyền)
    @PostMapping("/club/{clubId}")
    public ResponseEntity<?> createDept(
            @RequestHeader("X-User-Id") Integer managerId,
            @PathVariable Integer clubId,
            @RequestBody Map<String, String> body) {
        try {
            Department dept = departmentService.createDepartment(
                    managerId, clubId, body.get("name"), body.get("description"), body.get("colorHex"));
            return ResponseEntity.ok(dept);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getLocalizedMessage());
        }
    }

    // 2. Thêm thành viên vào phòng ban (Yêu cầu Operator là Manager hoặc HEAD của ban)
    @PostMapping("/{departmentId}/members")
    public ResponseEntity<?> addMember(
            @RequestHeader("X-User-Id") Integer operatorId,
            @PathVariable Long departmentId,
            @RequestBody Map<String, Object> body) {
        try {
            Integer targetUserId = Integer.valueOf(body.get("userId").toString());
            DepartmentRole role = DepartmentRole.valueOf(body.get("role").toString());
            DepartmentMember member = departmentService.addMemberToDepartment(operatorId, departmentId, targetUserId, role);
            return ResponseEntity.ok(member);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // 3. Lấy danh sách thành viên của ban
    @GetMapping("/{departmentId}/members")
    public ResponseEntity<List<DepartmentMember>> getMembers(@PathVariable Long departmentId) {
        return ResponseEntity.ok(departmentService.getMembers(departmentId));
    }
}
