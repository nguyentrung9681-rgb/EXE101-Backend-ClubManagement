package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubGooglePermission;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Repository.ClubGooglePermissionRepository;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class GooglePermissionService {

    private final ClubGooglePermissionRepository clubGooglePermissionRepository;
    private final ClubMemberRepository clubMemberRepository;

    public GooglePermissionService(ClubGooglePermissionRepository clubGooglePermissionRepository,
                                   ClubMemberRepository clubMemberRepository) {
        this.clubGooglePermissionRepository = clubGooglePermissionRepository;
        this.clubMemberRepository = clubMemberRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Trao & Thu hồi quyền GOOGLE SHEET
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GooglePermissionResponse grantSheetPermissions(Integer requesterUserId, Integer clubId, Integer targetUserId, GoogleSheetPermissionRequest request) {
        ClubMember requester = checkPresidentPrivilege(clubId, requesterUserId);
        ClubMember targetMember = checkActiveTargetMember(clubId, targetUserId, requesterUserId);

        Club club = requester.getClub();
        User targetUser = targetMember.getUser();
        User requesterUser = requester.getUser();

        ClubGooglePermission permission = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId)
                .orElse(ClubGooglePermission.builder()
                        .club(club)
                        .user(targetUser)
                        .build());

        if (request != null) {
            if (request.getCanCreate() != null) permission.setCanCreateSheet(request.getCanCreate());
            if (request.getCanDelete() != null) permission.setCanDeleteSheet(request.getCanDelete());
            if (request.getCanEditTitle() != null) permission.setCanEditSheetTitle(request.getCanEditTitle());
            if (request.getCanEditData() != null) permission.setCanEditSheetData(request.getCanEditData());
            if (request.getCanEditType() != null) permission.setCanEditSheetType(request.getCanEditType());
        }

        permission.setGrantedBy(requesterUser);
        ClubGooglePermission saved = clubGooglePermissionRepository.save(permission);
        return mapToResponse(saved);
    }

    @Transactional
    public GooglePermissionResponse revokeSheetPermissions(Integer requesterUserId, Integer clubId, Integer targetUserId, GoogleSheetPermissionRequest request) {
        checkPresidentPrivilege(clubId, requesterUserId);
        checkActiveTargetMember(clubId, targetUserId, requesterUserId);

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId);
        if (permissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Thành viên này hiện chưa được trao quyền Google Sheet nào!");
        }

        ClubGooglePermission permission = permissionOpt.get();

        boolean revokeAllSheet = (request == null || (request.getCanCreate() == null && request.getCanDelete() == null &&
                request.getCanEditTitle() == null && request.getCanEditData() == null && request.getCanEditType() == null));

        if (revokeAllSheet) {
            permission.setCanCreateSheet(false);
            permission.setCanDeleteSheet(false);
            permission.setCanEditSheetTitle(false);
            permission.setCanEditSheetData(false);
            permission.setCanEditSheetType(false);
        } else {
            if (Boolean.TRUE.equals(request.getCanCreate())) permission.setCanCreateSheet(false);
            if (Boolean.TRUE.equals(request.getCanDelete())) permission.setCanDeleteSheet(false);
            if (Boolean.TRUE.equals(request.getCanEditTitle())) permission.setCanEditSheetTitle(false);
            if (Boolean.TRUE.equals(request.getCanEditData())) permission.setCanEditSheetData(false);
            if (Boolean.TRUE.equals(request.getCanEditType())) permission.setCanEditSheetType(false);
        }

        if (isAllPermissionsFalse(permission)) {
            clubGooglePermissionRepository.delete(permission);
            return createEmptyResponse(clubId, targetUserId);
        }

        ClubGooglePermission saved = clubGooglePermissionRepository.save(permission);
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Trao & Thu hồi quyền GOOGLE FORM
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GooglePermissionResponse grantFormPermissions(Integer requesterUserId, Integer clubId, Integer targetUserId, GoogleFormPermissionRequest request) {
        ClubMember requester = checkPresidentPrivilege(clubId, requesterUserId);
        ClubMember targetMember = checkActiveTargetMember(clubId, targetUserId, requesterUserId);

        Club club = requester.getClub();
        User targetUser = targetMember.getUser();
        User requesterUser = requester.getUser();

        ClubGooglePermission permission = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId)
                .orElse(ClubGooglePermission.builder()
                        .club(club)
                        .user(targetUser)
                        .build());

        if (request != null) {
            if (request.getCanCreate() != null) permission.setCanCreateForm(request.getCanCreate());
            if (request.getCanDelete() != null) permission.setCanDeleteForm(request.getCanDelete());
            if (request.getCanEditTitle() != null) permission.setCanEditFormTitle(request.getCanEditTitle());
            if (request.getCanEditData() != null) permission.setCanEditFormData(request.getCanEditData());
            if (request.getCanEditType() != null) permission.setCanEditFormType(request.getCanEditType());
        }

        permission.setGrantedBy(requesterUser);
        ClubGooglePermission saved = clubGooglePermissionRepository.save(permission);
        return mapToResponse(saved);
    }

    @Transactional
    public GooglePermissionResponse revokeFormPermissions(Integer requesterUserId, Integer clubId, Integer targetUserId, GoogleFormPermissionRequest request) {
        checkPresidentPrivilege(clubId, requesterUserId);
        checkActiveTargetMember(clubId, targetUserId, requesterUserId);

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId);
        if (permissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Thành viên này hiện chưa được trao quyền Google Form nào!");
        }

        ClubGooglePermission permission = permissionOpt.get();

        boolean revokeAllForm = (request == null || (request.getCanCreate() == null && request.getCanDelete() == null &&
                request.getCanEditTitle() == null && request.getCanEditData() == null && request.getCanEditType() == null));

        if (revokeAllForm) {
            permission.setCanCreateForm(false);
            permission.setCanDeleteForm(false);
            permission.setCanEditFormTitle(false);
            permission.setCanEditFormData(false);
            permission.setCanEditFormType(false);
        } else {
            if (Boolean.TRUE.equals(request.getCanCreate())) permission.setCanCreateForm(false);
            if (Boolean.TRUE.equals(request.getCanDelete())) permission.setCanDeleteForm(false);
            if (Boolean.TRUE.equals(request.getCanEditTitle())) permission.setCanEditFormTitle(false);
            if (Boolean.TRUE.equals(request.getCanEditData())) permission.setCanEditFormData(false);
            if (Boolean.TRUE.equals(request.getCanEditType())) permission.setCanEditFormType(false);
        }

        if (isAllPermissionsFalse(permission)) {
            clubGooglePermissionRepository.delete(permission);
            return createEmptyResponse(clubId, targetUserId);
        }

        ClubGooglePermission saved = clubGooglePermissionRepository.save(permission);
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Trao & Thu hồi TỔNG HỢP (Sheet & Form)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public GooglePermissionResponse grantPermissions(Integer requesterUserId, Integer clubId, Integer targetUserId, GooglePermissionRequest request) {
        ClubMember requester = checkPresidentPrivilege(clubId, requesterUserId);
        ClubMember targetMember = checkActiveTargetMember(clubId, targetUserId, requesterUserId);

        Club club = requester.getClub();
        User targetUser = targetMember.getUser();
        User requesterUser = requester.getUser();

        ClubGooglePermission permission = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId)
                .orElse(ClubGooglePermission.builder()
                        .club(club)
                        .user(targetUser)
                        .build());

        if (request != null) {
            // Sheet
            if (request.getCanCreateSheet() != null) permission.setCanCreateSheet(request.getCanCreateSheet());
            if (request.getCanDeleteSheet() != null) permission.setCanDeleteSheet(request.getCanDeleteSheet());
            if (request.getCanEditSheetTitle() != null) permission.setCanEditSheetTitle(request.getCanEditSheetTitle());
            if (request.getCanEditSheetData() != null) permission.setCanEditSheetData(request.getCanEditSheetData());
            if (request.getCanEditSheetType() != null) permission.setCanEditSheetType(request.getCanEditSheetType());

            // Form
            if (request.getCanCreateForm() != null) permission.setCanCreateForm(request.getCanCreateForm());
            if (request.getCanDeleteForm() != null) permission.setCanDeleteForm(request.getCanDeleteForm());
            if (request.getCanEditFormTitle() != null) permission.setCanEditFormTitle(request.getCanEditFormTitle());
            if (request.getCanEditFormData() != null) permission.setCanEditFormData(request.getCanEditFormData());
            if (request.getCanEditFormType() != null) permission.setCanEditFormType(request.getCanEditFormType());
        }

        permission.setGrantedBy(requesterUser);
        ClubGooglePermission saved = clubGooglePermissionRepository.save(permission);
        return mapToResponse(saved);
    }

    @Transactional
    public GooglePermissionResponse revokePermissions(Integer requesterUserId, Integer clubId, Integer targetUserId, GooglePermissionRequest request) {
        checkPresidentPrivilege(clubId, requesterUserId);
        checkActiveTargetMember(clubId, targetUserId, requesterUserId);

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId);
        if (permissionOpt.isEmpty()) {
            throw new IllegalArgumentException("Thành viên này hiện chưa được trao quyền nào!");
        }

        ClubGooglePermission permission = permissionOpt.get();

        boolean revokeAll = (request == null);

        if (revokeAll) {
            clubGooglePermissionRepository.delete(permission);
            return createEmptyResponse(clubId, targetUserId);
        } else {
            // Sheet
            if (Boolean.TRUE.equals(request.getCanCreateSheet())) permission.setCanCreateSheet(false);
            if (Boolean.TRUE.equals(request.getCanDeleteSheet())) permission.setCanDeleteSheet(false);
            if (Boolean.TRUE.equals(request.getCanEditSheetTitle())) permission.setCanEditSheetTitle(false);
            if (Boolean.TRUE.equals(request.getCanEditSheetData())) permission.setCanEditSheetData(false);
            if (Boolean.TRUE.equals(request.getCanEditSheetType())) permission.setCanEditSheetType(false);

            // Form
            if (Boolean.TRUE.equals(request.getCanCreateForm())) permission.setCanCreateForm(false);
            if (Boolean.TRUE.equals(request.getCanDeleteForm())) permission.setCanDeleteForm(false);
            if (Boolean.TRUE.equals(request.getCanEditFormTitle())) permission.setCanEditFormTitle(false);
            if (Boolean.TRUE.equals(request.getCanEditFormData())) permission.setCanEditFormData(false);
            if (Boolean.TRUE.equals(request.getCanEditFormType())) permission.setCanEditFormType(false);

            if (isAllPermissionsFalse(permission)) {
                clubGooglePermissionRepository.delete(permission);
                return createEmptyResponse(clubId, targetUserId);
            }

            ClubGooglePermission saved = clubGooglePermissionRepository.save(permission);
            return mapToResponse(saved);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Lấy danh sách & Chi tiết quyền
    // ─────────────────────────────────────────────────────────────────────────

    public List<GooglePermissionResponse> getClubPermissions(Integer requesterUserId, Integer clubId) {
        checkActiveMember(clubId, requesterUserId);
        return clubGooglePermissionRepository.findByClubId(clubId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public GooglePermissionResponse getUserPermissions(Integer requesterUserId, Integer clubId, Integer targetUserId) {
        checkActiveMember(clubId, requesterUserId);

        ClubMember targetMember = clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, targetUserId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Thành viên không tồn tại hoặc không ACTIVE trong CLB này!"));

        if (targetMember.getRole() == ClubMemberRole.PRESIDENT || targetMember.getRole() == ClubMemberRole.TREASURER) {
            return GooglePermissionResponse.builder()
                    .clubId(clubId)
                    .userId(targetUserId)
                    .userFullName(targetMember.getUser() != null ? targetMember.getUser().getFullName() : null)
                    .userEmail(targetMember.getUser() != null ? targetMember.getUser().getEmail() : null)
                    .canCreateSheet(true)
                    .canDeleteSheet(true)
                    .canEditSheetTitle(true)
                    .canEditSheetData(true)
                    .canEditSheetType(true)
                    .canCreateForm(true)
                    .canDeleteForm(true)
                    .canEditFormTitle(true)
                    .canEditFormData(true)
                    .canEditFormType(true)
                    .build();
        }

        return clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, targetUserId)
                .map(this::mapToResponse)
                .orElseGet(() -> createEmptyResponse(clubId, targetUserId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ClubMember checkPresidentPrivilege(Integer clubId, Integer requesterUserId) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, requesterUserId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException("Bạn không phải là thành viên ACTIVE của câu lạc bộ này!"));

        if (member.getRole() != ClubMemberRole.PRESIDENT) {
            throw new SecurityException("Chỉ chủ nhiệm câu lạc bộ (PRESIDENT) mới có quyền thực hiện thao tác trao / thu hồi quyền!");
        }
        return member;
    }

    private ClubMember checkActiveTargetMember(Integer clubId, Integer targetUserId, Integer requesterUserId) {
        ClubMember targetMember = clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, targetUserId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Người dùng mục tiêu không phải là thành viên ACTIVE của câu lạc bộ này!"));

        if (targetUserId.equals(requesterUserId)) {
            throw new IllegalArgumentException("Chủ nhiệm đã có sẵn toàn quyền, không cần tự trao/thu hồi quyền của chính mình!");
        }
        return targetMember;
    }

    private boolean isAllPermissionsFalse(ClubGooglePermission p) {
        return !p.isCanCreateSheet() && !p.isCanDeleteSheet() && !p.isCanEditSheetTitle() && !p.isCanEditSheetData() && !p.isCanEditSheetType()
                && !p.isCanCreateForm() && !p.isCanDeleteForm() && !p.isCanEditFormTitle() && !p.isCanEditFormData() && !p.isCanEditFormType();
    }

    private GooglePermissionResponse createEmptyResponse(Integer clubId, Integer userId) {
        return GooglePermissionResponse.builder()
                .clubId(clubId)
                .userId(userId)
                .canCreateSheet(false)
                .canDeleteSheet(false)
                .canEditSheetTitle(false)
                .canEditSheetData(false)
                .canEditSheetType(false)
                .canCreateForm(false)
                .canDeleteForm(false)
                .canEditFormTitle(false)
                .canEditFormData(false)
                .canEditFormType(false)
                .build();
    }

    private ClubMember checkActiveMember(Integer clubId, Integer requesterUserId) {
        return clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, requesterUserId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException("Bạn không phải thành viên ACTIVE của câu lạc bộ này!"));
    }

    private GooglePermissionResponse mapToResponse(ClubGooglePermission permission) {
        if (permission == null) return null;
        return GooglePermissionResponse.builder()
                .id(permission.getId())
                .clubId(permission.getClub() != null ? permission.getClub().getId() : null)
                .userId(permission.getUser() != null ? permission.getUser().getUserId() : null)
                .userFullName(permission.getUser() != null ? permission.getUser().getFullName() : null)
                .userEmail(permission.getUser() != null ? permission.getUser().getEmail() : null)
                .canCreateSheet(permission.isCanCreateSheet())
                .canDeleteSheet(permission.isCanDeleteSheet())
                .canEditSheetTitle(permission.isCanEditSheetTitle())
                .canEditSheetData(permission.isCanEditSheetData())
                .canEditSheetType(permission.isCanEditSheetType())
                .canCreateForm(permission.isCanCreateForm())
                .canDeleteForm(permission.isCanDeleteForm())
                .canEditFormTitle(permission.isCanEditFormTitle())
                .canEditFormData(permission.isCanEditFormData())
                .canEditFormType(permission.isCanEditFormType())
                .grantedByUserId(permission.getGrantedBy() != null ? permission.getGrantedBy().getUserId() : null)
                .grantedByFullName(permission.getGrantedBy() != null ? permission.getGrantedBy().getFullName() : null)
                .updatedAt(permission.getUpdatedAt())
                .build();
    }
}
