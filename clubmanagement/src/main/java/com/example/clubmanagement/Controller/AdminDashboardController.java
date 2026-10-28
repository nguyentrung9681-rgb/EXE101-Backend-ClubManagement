package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Enum.SystemRole;
import com.example.clubmanagement.Service.AdminDashboardService;
import com.example.clubmanagement.dto.AdminClubResponse;
import com.example.clubmanagement.dto.AdminDashboardOverviewResponse;
import com.example.clubmanagement.dto.AdminUserResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@Tag(name = "Admin Dashboard", description = "Các API giám sát hệ thống, Dashboard, danh sách người dùng và danh sách câu lạc bộ")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    /**
     * Lấy dữ liệu tổng quan cho Dashboard Quản trị.
     * Bao gồm: Thống kê số lượng, Biểu đồ tài nguyên theo tháng (Documents, Events, Forms, Sheets),
     * Danh sách CLB hiện tại, và Hoạt động gần đây trên hệ thống.
     * GET /api/admin/dashboard/overview
     */
    @GetMapping("/dashboard/overview")
    @Operation(summary = "Lấy dữ liệu tổng quan Dashboard", description = "Trả về số lượng tổng quan, biểu đồ tháng, CLB hiện tại và hoạt động gần đây")
    public ResponseEntity<?> getDashboardOverview() {
        try {
            AdminDashboardOverviewResponse response = adminDashboardService.getDashboardOverview();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách toàn bộ người dùng sử dụng hệ thống.
     * Hỗ trợ tìm kiếm theo tên/email/username và lọc theo trạng thái (ACTIVE, INACTIVE, BANNED), authProvider (LOCAL, GOOGLE).
     * GET /api/admin/users
     */
    @GetMapping("/users")
    @Operation(summary = "Lấy danh sách người dùng trong hệ thống", description = "Hỗ trợ tìm kiếm và lọc người dùng kèm số lượng CLB tham gia/quản lý")
    public ResponseEntity<?> getAllUsers(
            @Parameter(description = "Từ khóa tìm kiếm (tên, email, username)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Trạng thái người dùng (ACTIVE, INACTIVE, BANNED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Phương thức đăng nhập (LOCAL, GOOGLE)")
            @RequestParam(required = false) String authProvider) {
        try {
            List<AdminUserResponse> users = adminDashboardService.getAllUsers(search, status, authProvider);
            return ResponseEntity.ok(users);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách toàn bộ Câu lạc bộ sử dụng trong hệ thống.
     * Hỗ trợ tìm kiếm theo tên/mô tả/chủ nhiệm và lọc theo trạng thái (ACTIVE, INACTIVE), visibility (PUBLIC, PRIVATE).
     * GET /api/admin/clubs
     */
    @GetMapping("/clubs")
    @Operation(summary = "Lấy danh sách các câu lạc bộ trong hệ thống", description = "Trả về thông tin chi tiết CLB, chủ nhiệm, số lượng thành viên, tài liệu và sự kiện")
    public ResponseEntity<?> getAllClubs(
            @Parameter(description = "Từ khóa tìm kiếm (tên CLB, mô tả, tên chủ nhiệm)")
            @RequestParam(required = false) String search,
            @Parameter(description = "Trạng thái CLB (ACTIVE, INACTIVE)")
            @RequestParam(required = false) String status,
            @Parameter(description = "Chế độ hiển thị (PUBLIC, PRIVATE)")
            @RequestParam(required = false) String visibility) {
        try {
            List<AdminClubResponse> clubs = adminDashboardService.getAllClubs(search, status, visibility);
            return ResponseEntity.ok(clubs);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cập nhật vai trò hệ thống của người dùng (USER / ADMIN).
     * PATCH /api/admin/users/{userId}/role
     */
    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "Cập nhật vai trò người dùng (USER / ADMIN)", description = "Chỉ tài khoản Admin mới có quyền thực hiện")
    public ResponseEntity<?> updateUserRole(
            @Parameter(description = "ID của người dùng cần đổi vai trò")
            @PathVariable Integer userId,
            @RequestBody Map<String, String> request) {
        try {
            String roleStr = request.get("role");
            if (roleStr == null || roleStr.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng cung cấp trường 'role' (USER hoặc ADMIN)!"));
            }
            SystemRole newRole;
            try {
                newRole = SystemRole.valueOf(roleStr.trim().toUpperCase());
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vai trò không hợp lệ! Chỉ chấp nhận: USER hoặc ADMIN."));
            }
            adminDashboardService.updateUserRole(userId, newRole);
            return ResponseEntity.ok(Map.of(
                    "message", "Cập nhật vai trò người dùng thành công!",
                    "userId", userId,
                    "newRole", newRole.name()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
