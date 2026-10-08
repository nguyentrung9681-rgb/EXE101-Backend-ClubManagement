package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubGooglePermission;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Repository.ClubGooglePermissionRepository;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.GoogleAccountRepository;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service tập trung toàn bộ logic kiểm tra phân quyền độc lập cho Google Sheet & Google Form.
 *
 * <p>Các ràng buộc:
 * <ol>
 *   <li>Người dùng hoặc Chủ CLB <b>phải</b> có Google Account được liên kết.</li>
 *   <li>Người dùng <b>phải</b> là thành viên ACTIVE của CLB đang thao tác.</li>
 *   <li>Mặc định <b>PRESIDENT</b> và <b>TREASURER</b> có toàn quyền.</li>
 *   <li>Các thành viên khác có quyền nếu được <b>Chủ club (PRESIDENT)</b> trao quyền cụ thể qua ClubGooglePermission.</li>
 *   <li>Mọi thành viên ACTIVE đều được Xem.</li>
 * </ol>
 */
@Service
public class ClubPermissionService {

    private final ClubMemberRepository clubMemberRepository;
    private final GoogleAccountRepository googleAccountRepository;
    private final ClubGooglePermissionRepository clubGooglePermissionRepository;

    public ClubPermissionService(ClubMemberRepository clubMemberRepository,
                                  GoogleAccountRepository googleAccountRepository,
                                  ClubGooglePermissionRepository clubGooglePermissionRepository) {
        this.clubMemberRepository = clubMemberRepository;
        this.googleAccountRepository = googleAccountRepository;
        this.clubGooglePermissionRepository = clubGooglePermissionRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Kiểm tra liên kết Google Account (Thử user -> Fallback Chủ CLB)
    // ─────────────────────────────────────────────────────────────────────────

    public void requireGoogleAccount(Integer userId, Integer clubId) {
        boolean hasUserAccount = googleAccountRepository
                .findFirstByUserUserIdOrderByCreatedAtDesc(userId)
                .isPresent();
        if (hasUserAccount) {
            return;
        }

        // Fallback: Kiểm tra xem Chủ CLB (PRESIDENT) có tài khoản Google được liên kết không
        if (clubId != null) {
            boolean hasPresidentAccount = clubMemberRepository.findByClubId(clubId).stream()
                    .filter(m -> m.getRole() == ClubMemberRole.PRESIDENT && m.getStatus() == ClubMemberStatus.ACTIVE)
                    .findFirst()
                    .map(m -> googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(m.getUser().getUserId()).isPresent())
                    .orElse(false);

            if (hasPresidentAccount) {
                return;
            }
        }

        throw new SecurityException(
                "Chưa tìm thấy liên kết tài khoản Google hợp lệ. " +
                "Vui lòng kết nối Google Account hoặc yêu cầu Chủ CLB kết nối tài khoản Google!");
    }

    public void requireGoogleAccount(Integer userId) {
        requireGoogleAccount(userId, null);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Kiểm tra tư cách thành viên CLB
    // ─────────────────────────────────────────────────────────────────────────

    public ClubMember requireActiveMember(Integer userId, Integer clubId) {
        return clubMemberRepository
                .findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException(
                        "Bạn không phải thành viên ACTIVE của CLB này. " +
                        "Chỉ thành viên trong CLB mới được truy cập nội dung của CLB."));
    }

    private boolean isDefaultPrivileged(ClubMemberRole role) {
        return role == ClubMemberRole.PRESIDENT || role == ClubMemberRole.TREASURER;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Kiểm tra quyền GOOGLE SHEET
    // ─────────────────────────────────────────────────────────────────────────

    public void requireCanCreateSheet(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanCreateSheet()) {
            throw new SecurityException("Bạn không có quyền tạo Google Sheet trong CLB này.");
        }
    }

    public void requireCanDeleteSheet(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanDeleteSheet()) {
            throw new SecurityException("Bạn không có quyền xóa Google Sheet trong CLB này.");
        }
    }

    public void requireCanEditSheetTitle(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanEditSheetTitle()) {
            throw new SecurityException("Bạn không có quyền chỉnh sửa tiêu đề Google Sheet trong CLB này.");
        }
    }

    public void requireCanEditSheetData(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanEditSheetData()) {
            throw new SecurityException("Bạn không có quyền cập nhật dữ liệu Google Sheet trong CLB này.");
        }
    }

    public void requireCanEditSheetType(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanEditSheetType()) {
            throw new SecurityException("Bạn không có quyền cập nhật phân loại Google Sheet trong CLB này.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Kiểm tra quyền GOOGLE FORM
    // ─────────────────────────────────────────────────────────────────────────

    public void requireCanCreateForm(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanCreateForm()) {
            throw new SecurityException("Bạn không có quyền tạo Google Form trong CLB này.");
        }
    }

    public void requireCanDeleteForm(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanDeleteForm()) {
            throw new SecurityException("Bạn không có quyền xóa Google Form trong CLB này.");
        }
    }

    public void requireCanEditFormTitle(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanEditFormTitle()) {
            throw new SecurityException("Bạn không có quyền chỉnh sửa tiêu đề Google Form trong CLB này.");
        }
    }

    public void requireCanEditFormData(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanEditFormData()) {
            throw new SecurityException("Bạn không có quyền cập nhật dữ liệu / câu hỏi Google Form trong CLB này.");
        }
    }

    public void requireCanEditFormType(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        ClubMember member = requireActiveMember(userId, clubId);

        if (isDefaultPrivileged(member.getRole())) return;

        Optional<ClubGooglePermission> permissionOpt = clubGooglePermissionRepository.findByClubIdAndUserUserId(clubId, userId);
        if (permissionOpt.isEmpty() || !permissionOpt.get().isCanEditFormType()) {
            throw new SecurityException("Bạn không có quyền cập nhật phân loại Google Form trong CLB này.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. General / Read / Comment checks & Legacy compatibility
    // ─────────────────────────────────────────────────────────────────────────

    public void requireCanView(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        requireActiveMember(userId, clubId);
    }

    public void requireCanComment(Integer userId, Integer clubId) {
        requireGoogleAccount(userId, clubId);
        requireActiveMember(userId, clubId);
    }

    public void requireCanCreate(Integer userId, Integer clubId) {
        requireCanCreateSheet(userId, clubId);
    }

    public void requireCanDelete(Integer userId, Integer clubId) {
        requireCanDeleteSheet(userId, clubId);
    }

    public void requireCanEditTitle(Integer userId, Integer clubId) {
        requireCanEditSheetTitle(userId, clubId);
    }

    public void requireCanEditData(Integer userId, Integer clubId) {
        requireCanEditSheetData(userId, clubId);
    }

    public void requireCanWrite(Integer userId, Integer clubId) {
        requireCanEditSheetData(userId, clubId);
    }

    public void requireCanEditType(Integer userId, Integer clubId) {
        requireCanEditSheetType(userId, clubId);
    }

    public ClubMemberRole getMemberRole(Integer userId, Integer clubId) {
        return clubMemberRepository
                .findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                .map(ClubMember::getRole)
                .orElse(null);
    }
}
