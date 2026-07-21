package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Enum.ClubVisibility;
import com.example.clubmanagement.Service.ClubService;
import com.example.clubmanagement.dto.ClubRequest;
import com.example.clubmanagement.dto.ClubResponse;
import com.example.clubmanagement.dto.ClubMemberResponse;
import com.example.clubmanagement.dto.UpdateMemberRoleDeptRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    /**
     * Tạo một Câu lạc bộ mới.
     * POST /api/clubs?userId=1
     */
    @PostMapping
    public ResponseEntity<?> createClub(@RequestBody ClubRequest clubRequest, @RequestParam Integer userId) {
        try {
            ClubVisibility visibility = ClubVisibility.PUBLIC;
            if (clubRequest.getVisibility() != null) {
                try {
                    visibility = ClubVisibility.valueOf(clubRequest.getVisibility().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body("Giá trị visibility không hợp lệ (hợp lệ: PUBLIC, PRIVATE)");
                }
            }
            Club club = Club.builder()
                    .name(clubRequest.getName())
                    .description(clubRequest.getDescription())
                    .logoUrl(clubRequest.getLogoUrl())
                    .visibility(visibility)
                    .build();
            Club created = clubService.createClub(club, userId);
            return ResponseEntity.ok(mapToClubResponse(created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách tất cả Câu lạc bộ.
     * GET /api/clubs
     */
    @GetMapping
    public ResponseEntity<List<ClubResponse>> getAllClubs() {
        List<ClubResponse> responses = clubService.getAllClubs().stream()
                .map(this::mapToClubResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Lấy thông tin Câu lạc bộ theo ID.
     * GET /api/clubs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getClubById(@PathVariable Integer id) {
        try {
            Club club = clubService.getClubById(id);
            return ResponseEntity.ok(mapToClubResponse(club));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách Câu lạc bộ của một người dùng cùng với vai trò.
     * GET /api/clubs/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserClubs(@PathVariable Integer userId) {
        try {
            List<ClubMemberResponse> responses = clubService.getUserMemberships(userId).stream()
                    .map(this::mapToClubMemberResponse)
                    .collect(Collectors.toList());
            if (responses.isEmpty()) {
                throw new RuntimeException("Người dùng chưa tham gia câu lạc bộ nào!");
            }
            return ResponseEntity.ok(responses);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private ClubResponse mapToClubResponse(Club club) {
        if (club == null) return null;
        return ClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(club.getLogoUrl())
                .status(club.getStatus() != null ? club.getStatus().name() : null)
                .visibility(club.getVisibility() != null ? club.getVisibility().name() : ClubVisibility.PUBLIC.name())
                .createdByUserId(club.getCreatedBy() != null ? club.getCreatedBy().getUserId() : null)
                .createdByName(club.getCreatedBy() != null ? club.getCreatedBy().getFullName() : null)
                .createdAt(club.getCreatedAt())
                .updatedAt(club.getUpdatedAt())
                .build();
    }

    /**
     * Yêu cầu tham gia câu lạc bộ.
     * POST /api/clubs/{clubId}/join?userId={userId}
     */
    @PostMapping("/{clubId}/join")
    public ResponseEntity<?> joinClub(@PathVariable Integer clubId, @RequestParam Integer userId) {
        try {
            ClubMember member = clubService.joinClub(clubId, userId);
            String message = "Tham gia câu lạc bộ thành công!";
            if (member.getStatus() == com.example.clubmanagement.Enum.ClubMemberStatus.PENDING) {
                message = "Yêu cầu tham gia câu lạc bộ đã được gửi, vui lòng chờ chủ nhiệm phê duyệt!";
            }
            Map<String, Object> response = new HashMap<>();
            response.put("message", message);
            response.put("member", mapToClubMemberResponse(member));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách thành viên chờ duyệt (Chỉ dành cho chủ nhiệm).
     * GET /api/clubs/{clubId}/members/pending?requesterUserId={requesterUserId}
     */
    @GetMapping("/{clubId}/members/pending")
    public ResponseEntity<?> getPendingMembers(@PathVariable Integer clubId, @RequestParam Integer requesterUserId) {
        try {
            List<ClubMemberResponse> pending = clubService.getPendingMembers(clubId, requesterUserId).stream()
                    .map(this::mapToClubMemberResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(pending);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Phê duyệt hoặc từ chối thành viên tham gia câu lạc bộ (Chỉ dành cho chủ nhiệm).
     * PUT /api/clubs/{clubId}/members/{memberId}/approve?requesterUserId={requesterUserId}&approve={approve}
     */
    @PutMapping("/{clubId}/members/{memberId}/approve")
    public ResponseEntity<?> approveMember(
            @PathVariable Integer clubId,
            @PathVariable Integer memberId,
            @RequestParam Integer requesterUserId,
            @RequestParam boolean approve) {
        try {
            ClubMember member = clubService.approveMember(clubId, memberId, requesterUserId, approve);
            if (approve) {
                return ResponseEntity.ok(mapToClubMemberResponse(member));
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Đã từ chối yêu cầu tham gia của thành viên!");
                return ResponseEntity.ok(response);
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách thành viên đang hoạt động của Câu lạc bộ.
     * GET /api/clubs/{clubId}/members
     */
    @GetMapping("/{clubId}/members")
    public ResponseEntity<?> getClubMembers(@PathVariable Integer clubId) {
        try {
            List<ClubMemberResponse> members = clubService.getClubMembers(clubId).stream()
                    .map(this::mapToClubMemberResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(members);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private ClubMemberResponse mapToClubMemberResponse(ClubMember member) {
        if (member == null) return null;
        return ClubMemberResponse.builder()
                .id(member.getId())
                .clubId(member.getClub() != null ? member.getClub().getId() : null)
                .clubName(member.getClub() != null ? member.getClub().getName() : null)
                .userId(member.getUser() != null ? member.getUser().getUserId() : null)
                .fullName(member.getUser() != null ? member.getUser().getFullName() : null)
                .email(member.getUser() != null ? member.getUser().getEmail() : null)
                .role(member.getRole() != null ? member.getRole().name() : null)
                .status(member.getStatus() != null ? member.getStatus().name() : null)
                .departmentId(member.getDepartment() != null ? member.getDepartment().getId() : null)
                .departmentName(member.getDepartment() != null ? member.getDepartment().getName() : "None")
                .joinedAt(member.getJoinedAt())
                .build();
    }

    /**
     * Cập nhật vai trò và phòng ban của thành viên (Chỉ chủ nhiệm mới có quyền).
     * PUT /api/clubs/{clubId}/members/{memberId}?requesterUserId={id}&role={role}&departmentId={departmentId}
     */
    @PutMapping("/{clubId}/members/{memberId}")
    public ResponseEntity<?> updateMemberRoleDept(
            @PathVariable Integer clubId,
            @PathVariable Integer memberId,
            @RequestParam Integer requesterUserId,
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"DEPARTMENT_HEAD", "TREASURER", "MEMBER"}), description = "Vai trò mới gán cho thành viên")
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer departmentId) {
        try {
            UpdateMemberRoleDeptRequest request = UpdateMemberRoleDeptRequest.builder()
                    .role(role)
                    .departmentId(departmentId)
                    .build();
            ClubMember updated = clubService.updateMemberRoleDept(clubId, memberId, requesterUserId, request);
            return ResponseEntity.ok(mapToClubMemberResponse(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Tạm khóa/mở khóa thành viên câu lạc bộ (Chủ nhiệm chuyển trạng thái User thành INACTIVE).
     * PUT /api/clubs/{clubId}/members/{memberId}/lock?requesterUserId={id}&lock={true|false}
     */
    @PutMapping("/{clubId}/members/{memberId}/lock")
    public ResponseEntity<?> lockMember(
            @PathVariable Integer clubId,
            @PathVariable Integer memberId,
            @RequestParam Integer requesterUserId,
            @RequestParam boolean lock) {
        try {
            clubService.lockMember(clubId, memberId, requesterUserId, lock);
            String message = lock ? "Đã khóa tài khoản thành viên thành công!" : "Đã mở khóa tài khoản thành viên thành công!";
            Map<String, String> response = new HashMap<>();
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
