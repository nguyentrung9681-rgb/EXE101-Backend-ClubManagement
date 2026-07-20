package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.GoogleAccountRepository;
import org.springframework.stereotype.Service;

/**
 * Service tập trung toàn bộ logic kiểm tra phân quyền cho Google Sheet & Google Form.
 *
 * <p>Các ràng buộc:
 * <ol>
 *   <li>Người dùng <b>phải</b> có Google Account được liên kết.</li>
 *   <li>Người dùng <b>phải</b> là thành viên ACTIVE của CLB đang thao tác.</li>
 *   <li>Chỉ <b>PRESIDENT</b> hoặc <b>TREASURER</b> mới được Tạo / Ghi / Xóa.</li>
 *   <li>Mọi thành viên ACTIVE đều được Xem và Comment (thêm câu hỏi).</li>
 * </ol>
 */
@Service
public class ClubPermissionService {

    private final ClubMemberRepository clubMemberRepository;
    private final GoogleAccountRepository googleAccountRepository;

    public ClubPermissionService(ClubMemberRepository clubMemberRepository,
                                 GoogleAccountRepository googleAccountRepository) {
        this.clubMemberRepository = clubMemberRepository;
        this.googleAccountRepository = googleAccountRepository;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Kiểm tra liên kết Google Account
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Ném SecurityException nếu người dùng chưa liên kết tài khoản Google.
     * Bắt buộc gọi trước mọi thao tác với Google Sheet / Google Form.
     */
    public void requireGoogleAccount(Integer userId) {
        boolean hasAccount = googleAccountRepository
                .findFirstByUserUserIdOrderByCreatedAtDesc(userId)
                .isPresent();
        if (!hasAccount) {
            throw new SecurityException(
                    "Bạn chưa liên kết tài khoản Google. " +
                    "Vui lòng kết nối Google Account trước khi sử dụng tính năng này.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Kiểm tra tư cách thành viên CLB
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Lấy thông tin thành viên ACTIVE, ném SecurityException nếu không tìm thấy.
     */
    public ClubMember requireActiveMember(Integer userId, Integer clubId) {
        return clubMemberRepository
                .findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException(
                        "Bạn không phải thành viên ACTIVE của CLB này. " +
                        "Chỉ thành viên trong CLB mới được truy cập nội dung của CLB."));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Kiểm tra quyền theo hành động
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Kiểm tra quyền TẠO / GHI / XÓA: chỉ PRESIDENT hoặc TREASURER.
     */
    public void requireCanCreate(Integer userId, Integer clubId) {
        requireGoogleAccount(userId);
        ClubMember member = requireActiveMember(userId, clubId);

        ClubMemberRole role = member.getRole();
        if (role != ClubMemberRole.PRESIDENT && role != ClubMemberRole.TREASURER) {
            throw new SecurityException(
                    "Bạn không có quyền tạo file trong CLB này. " +
                    "Chỉ PRESIDENT hoặc TREASURER mới có thể tạo Google Sheet / Google Form.");
        }
    }

    /**
     * Kiểm tra quyền XEM: mọi thành viên ACTIVE.
     */
    public void requireCanView(Integer userId, Integer clubId) {
        requireGoogleAccount(userId);
        requireActiveMember(userId, clubId);
        // Tất cả thành viên ACTIVE đều được xem — không cần kiểm tra role thêm.
    }

    /**
     * Kiểm tra quyền COMMENT (thêm câu hỏi vào Form, ghi comment vào Sheet):
     * mọi thành viên ACTIVE.
     */
    public void requireCanComment(Integer userId, Integer clubId) {
        requireGoogleAccount(userId);
        requireActiveMember(userId, clubId);
        // Tất cả thành viên ACTIVE đều được comment — không cần kiểm tra role thêm.
    }

    /**
     * Kiểm tra quyền XÓA: chỉ PRESIDENT hoặc TREASURER.
     */
    public void requireCanDelete(Integer userId, Integer clubId) {
        requireGoogleAccount(userId);
        ClubMember member = requireActiveMember(userId, clubId);

        ClubMemberRole role = member.getRole();
        if (role != ClubMemberRole.PRESIDENT && role != ClubMemberRole.TREASURER) {
            throw new SecurityException(
                    "Bạn không có quyền xóa file này. " +
                    "Chỉ PRESIDENT hoặc TREASURER mới có thể xóa Google Sheet / Google Form.");
        }
    }

    /**
     * Kiểm tra quyền GHI DỮ LIỆU vào Sheet: chỉ PRESIDENT hoặc TREASURER.
     */
    public void requireCanWrite(Integer userId, Integer clubId) {
        requireGoogleAccount(userId);
        ClubMember member = requireActiveMember(userId, clubId);

        ClubMemberRole role = member.getRole();
        if (role != ClubMemberRole.PRESIDENT && role != ClubMemberRole.TREASURER) {
            throw new SecurityException(
                    "Bạn không có quyền cập nhật dữ liệu trong CLB này. " +
                    "Chỉ PRESIDENT hoặc TREASURER mới có thể ghi dữ liệu vào Google Sheet.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Helper: lấy role hiện tại của thành viên (tiện dùng ở nơi khác)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Trả về role của thành viên trong CLB (không ném exception nếu không tồn tại).
     * Trả về null nếu người dùng không phải thành viên ACTIVE.
     */
    public ClubMemberRole getMemberRole(Integer userId, Integer clubId) {
        return clubMemberRepository
                .findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                .map(ClubMember::getRole)
                .orElse(null);
    }
}
