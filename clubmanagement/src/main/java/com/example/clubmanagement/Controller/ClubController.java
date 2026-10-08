package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Enum.ClubVisibility;
import com.example.clubmanagement.Service.ClubService;
import com.example.clubmanagement.dto.ClubRequest;
import com.example.clubmanagement.dto.ClubResponse;
import com.example.clubmanagement.dto.ClubMemberResponse;
import com.example.clubmanagement.dto.UpdateMemberRoleDeptRequest;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> createClub(@RequestBody ClubRequest clubRequest, @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            ClubVisibility visibility = ClubVisibility.PUBLIC;
            if (clubRequest.getVisibility() != null) {
                try {
                    visibility = ClubVisibility.valueOf(clubRequest.getVisibility().toUpperCase());
                } catch (IllegalArgumentException e) {
                    return ResponseEntity.badRequest().body(Map.of("error", "Giá trị visibility không hợp lệ (hợp lệ: PUBLIC, PRIVATE)"));
                }
            }
            Club club = Club.builder()
                    .name(clubRequest.getName())
                    .description(clubRequest.getDescription())
                    .logoUrl(clubRequest.getLogoUrl())
                    .visibility(visibility)
                    .build();
            Club created = clubService.createClub(club, effectiveUserId);
            return ResponseEntity.ok(mapToClubResponse(created));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách Câu lạc bộ của một người dùng cùng với vai trò.
     * GET /api/clubs/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserClubs(@PathVariable Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            List<ClubMemberResponse> responses = clubService.getUserMemberships(effectiveUserId).stream()
                    .map(this::mapToClubMemberResponse)
                    .collect(Collectors.toList());
            if (responses.isEmpty()) {
                throw new RuntimeException("Người dùng chưa tham gia câu lạc bộ nào!");
            }
            return ResponseEntity.ok(responses);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Yêu cầu tham gia câu lạc bộ.
     * POST /api/clubs/{clubId}/join?userId={userId}
     */
    @PostMapping("/{clubId}/join")
    public ResponseEntity<?> joinClub(@PathVariable Integer clubId, @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            ClubMember member = clubService.joinClub(clubId, effectiveUserId);
            String message = "Tham gia câu lạc bộ thành công!";
            if (member.getStatus() == com.example.clubmanagement.Enum.ClubMemberStatus.PENDING) {
                message = "Yêu cầu tham gia câu lạc bộ đã được gửi, vui lòng chờ chủ nhiệm phê duyệt!";
            }
            Map<String, Object> response = new HashMap<>();
            response.put("message", message);
            response.put("member", mapToClubMemberResponse(member));
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách thành viên chờ duyệt (Chỉ dành cho chủ nhiệm).
     * GET /api/clubs/{clubId}/members/pending?requesterUserId={requesterUserId}
     */
    @GetMapping("/{clubId}/members/pending")
    public ResponseEntity<?> getPendingMembers(@PathVariable Integer clubId, @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            List<ClubMemberResponse> pending = clubService.getPendingMembers(clubId, effectiveUserId).stream()
                    .map(this::mapToClubMemberResponse)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(pending);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            @RequestParam(required = false) Integer requesterUserId,
            @RequestParam boolean approve) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            ClubMember member = clubService.approveMember(clubId, memberId, effectiveUserId, approve);
            if (approve) {
                return ResponseEntity.ok(mapToClubMemberResponse(member));
            } else {
                Map<String, String> response = new HashMap<>();
                response.put("message", "Đã từ chối yêu cầu tham gia của thành viên!");
                return ResponseEntity.ok(response);
            }
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Cập nhật vai trò và phòng ban của thành viên (Chỉ chủ nhiệm mới có quyền).
     * PUT /api/clubs/{clubId}/members/{memberId}?requesterUserId={id}&role={role}&departmentId={departmentId}
     */
    @PutMapping("/{clubId}/members/{memberId}")
    public ResponseEntity<?> updateMemberRoleDept(
            @PathVariable Integer clubId,
            @PathVariable Integer memberId,
            @RequestParam(required = false) Integer requesterUserId,
            @io.swagger.v3.oas.annotations.Parameter(schema = @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"DEPARTMENT_HEAD", "TREASURER", "MEMBER"}), description = "Vai trò mới gán cho thành viên")
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer departmentId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            UpdateMemberRoleDeptRequest request = UpdateMemberRoleDeptRequest.builder()
                    .role(role)
                    .departmentId(departmentId)
                    .build();
            ClubMember updated = clubService.updateMemberRoleDept(clubId, memberId, effectiveUserId, request);
            return ResponseEntity.ok(mapToClubMemberResponse(updated));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Trao quyền chủ nhiệm cho một thành viên khác.
     * Người chủ nhiệm hiện tại sẽ chuyển thành vai trò MEMBER.
     * PUT /api/clubs/{clubId}/transfer-president?targetMemberId={targetMemberId}&requesterUserId={requesterUserId}
     */
    @PutMapping("/{clubId}/transfer-president")
    public ResponseEntity<?> transferPresident(
            @PathVariable Integer clubId,
            @RequestParam Integer targetMemberId,
            @RequestParam(required = false) Integer requesterUserId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            ClubMember newPresident = clubService.transferPresident(clubId, targetMemberId, effectiveUserId);

            String newPresidentName = (newPresident != null && newPresident.getUser() != null && newPresident.getUser().getFullName() != null)
                    ? newPresident.getUser().getFullName()
                    : ("ID " + targetMemberId);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Đã trao quyền chủ nhiệm cho thành viên " + newPresidentName + " thành công!");
            response.put("newPresident", mapToClubMemberResponse(newPresident));
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
            @RequestParam(required = false) Integer requesterUserId,
            @RequestParam boolean lock) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(requesterUserId);
            clubService.lockMember(clubId, memberId, effectiveUserId, lock);
            String message = lock ? "Đã khóa tài khoản thành viên thành công!" : "Đã mở khóa tài khoản thành viên thành công!";
            Map<String, String> response = new HashMap<>();
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Xóa câu lạc bộ theo ID (Chỉ người tạo câu lạc bộ mới có quyền xóa).
     * DELETE /api/clubs/{id}?userId={userId}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteClub(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            clubService.deleteClub(id, effectiveUserId);
            return ResponseEntity.ok(Map.of("message", "Xóa câu lạc bộ thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Trao quyền xóa câu lạc bộ cho một người dùng khác (Chỉ người tạo câu lạc bộ mới được phép làm).
     * POST /api/clubs/{id}/grant-deletion-permission?targetUserId={targetUserId}&userId={userId}
     */
    /**
     * Trao quyền xóa câu lạc bộ cho một người dùng khác (Chỉ người tạo câu lạc bộ mới được phép làm).
     * POST /api/clubs/{id}/grant-deletion-permission?targetUserId={targetUserId}&userId={userId}
     */
    @PostMapping("/{id}/grant-deletion-permission")
    public ResponseEntity<?> grantDeletionPermission(
            @PathVariable Integer id,
            @RequestParam Integer targetUserId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            Club updated = clubService.grantDeletionPermission(id, targetUserId, effectiveUserId);
            ClubResponse responseDto = mapToClubResponse(updated);

            String targetName = "ID " + targetUserId;
            if (responseDto.getDeletionPermittedUsers() != null) {
                targetName = responseDto.getDeletionPermittedUsers().stream()
                        .filter(u -> u.getUserId().equals(targetUserId))
                        .map(ClubResponse.UserSummary::getFullName)
                        .findFirst().orElse("ID " + targetUserId);
            }
            String message = "Bạn đã trao quyền xóa câu lạc bộ cho " + targetName + " thành công!";

            Map<String, Object> response = new HashMap<>();
            response.put("message", message);
            response.put("club", responseDto);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Thu hồi quyền xóa câu lạc bộ.
     * POST /api/clubs/{id}/revoke-deletion-permission?targetUserId={targetUserId}&userId={userId}
     * - Nếu truyền targetUserId: Thu hồi đích danh 1 người.
     * - Nếu để trống targetUserId: Thu hồi tất cả những người đã được trao quyền.
     */
    @PostMapping("/{id}/revoke-deletion-permission")
    public ResponseEntity<?> revokeDeletionPermission(
            @PathVariable Integer id,
            @RequestParam(required = false) Integer targetUserId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            Club currentClub = clubService.getClubById(id);

            String targetName = "tất cả người dùng được trao quyền";
            if (targetUserId != null && currentClub.getDeletionPermittedUsers() != null) {
                targetName = currentClub.getDeletionPermittedUsers().stream()
                        .filter(u -> u.getUserId().equals(targetUserId))
                        .map(u -> u.getFullName() != null ? u.getFullName() : ("ID " + targetUserId))
                        .findFirst().orElse("ID " + targetUserId);
            }

            Club updated = clubService.revokeDeletionPermission(id, targetUserId, effectiveUserId);
            ClubResponse responseDto = mapToClubResponse(updated);
            String message = (targetUserId != null)
                    ? "Bạn đã thu hồi quyền xóa câu lạc bộ từ " + targetName + " thành công!"
                    : "Bạn đã thu hồi quyền xóa câu lạc bộ từ tất cả những người được trao quyền!";

            Map<String, Object> response = new HashMap<>();
            response.put("message", message);
            response.put("club", responseDto);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy danh sách những người dùng được trao quyền xóa của một câu lạc bộ.
     * GET /api/clubs/{id}/deletion-permissions
     */
    @GetMapping("/{id}/deletion-permissions")
    public ResponseEntity<?> getDeletionPermittedUsers(@PathVariable Integer id) {
        try {
            Club club = clubService.getClubById(id);
            ClubResponse responseDto = mapToClubResponse(club);
            List<ClubResponse.UserSummary> permittedUsers = (responseDto != null && responseDto.getDeletionPermittedUsers() != null)
                    ? responseDto.getDeletionPermittedUsers()
                    : List.of();
            return ResponseEntity.ok(permittedUsers);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private ClubResponse mapToClubResponse(Club club) {
        if (club == null) return null;

        List<ClubResponse.UserSummary> permittedUsers = null;
        if (club.getDeletionPermittedUsers() != null && !club.getDeletionPermittedUsers().isEmpty()) {
            permittedUsers = club.getDeletionPermittedUsers().stream()
                    .map(u -> ClubResponse.UserSummary.builder()
                            .userId(u.getUserId())
                            .fullName(u.getFullName())
                            .email(u.getEmail())
                            .build())
                    .collect(Collectors.toList());
        }

        return ClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(club.getLogoUrl())
                .status(club.getStatus() != null ? club.getStatus().name() : null)
                .visibility(club.getVisibility() != null ? club.getVisibility().name() : ClubVisibility.PUBLIC.name())
                .createdByUserId(club.getCreatedBy() != null ? club.getCreatedBy().getUserId() : null)
                .createdByName(club.getCreatedBy() != null ? club.getCreatedBy().getFullName() : null)
                .deletionPermittedUsers(permittedUsers)
                .createdAt(club.getCreatedAt())
                .updatedAt(club.getUpdatedAt())
                .build();
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
}
