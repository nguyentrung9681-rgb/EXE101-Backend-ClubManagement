package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.Department;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.ClubStatus;
import com.example.clubmanagement.Enum.ClubVisibility;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.DepartmentRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.UpdateMemberRoleDeptRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    public ClubService(ClubRepository clubRepository, ClubMemberRepository clubMemberRepository, UserRepository userRepository, DepartmentRepository departmentRepository) {
        this.clubRepository = clubRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
    }

    /**
     * Tạo một câu lạc bộ mới và tự động gán người tạo làm Chủ nhiệm (PRESIDENT).
     */
    @Transactional
    public Club createClub(Club club, Integer userId) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        club.setCreatedBy(creator);
        club.setStatus(ClubStatus.ACTIVE);
        Club savedClub = clubRepository.save(club);

        // Tự động thêm chủ nhiệm vào danh sách thành viên
        ClubMember president = ClubMember.builder()
                .club(savedClub)
                .user(creator)
                .role(ClubMemberRole.PRESIDENT)
                .status(ClubMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        clubMemberRepository.save(president);

        return savedClub;
    }

    public List<Club> getAllClubs() {
        return clubRepository.findAll();
    }

    public Club getClubById(Integer id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
    }

    public List<ClubMember> getUserMemberships(Integer userId) {
        return clubMemberRepository.findByUserUserId(userId);
    }

    /**
     * Tham gia câu lạc bộ.
     */
    @Transactional
    public ClubMember joinClub(Integer clubId, Integer userId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        // Kiểm tra xem người dùng đã tham gia câu lạc bộ này chưa (bao gồm cả trường hợp đã tham gia, rời đi, hoặc bị từ chối trước đó)
        ClubMember existingMember = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId).orElse(null);

        if (existingMember != null) {
            if (existingMember.getStatus() == ClubMemberStatus.ACTIVE) {
                throw new RuntimeException("Bạn đã là thành viên của câu lạc bộ này!");
            } else if (existingMember.getStatus() == ClubMemberStatus.PENDING) {
                throw new RuntimeException("Yêu cầu tham gia của bạn đang chờ phê duyệt!");
            }
            // Nếu là LEFT hoặc đã bị từ chối trước đó, chúng ta có thể cập nhật lại bản ghi này để xin tham gia lại
            ClubMemberStatus status = (club.getVisibility() == ClubVisibility.PRIVATE) ? ClubMemberStatus.PENDING : ClubMemberStatus.ACTIVE;
            LocalDateTime joinedAt = (status == ClubMemberStatus.ACTIVE) ? LocalDateTime.now() : null;

            existingMember.setStatus(status);
            existingMember.setRole(ClubMemberRole.MEMBER);
            existingMember.setJoinedAt(joinedAt);
            return clubMemberRepository.save(existingMember);
        }

        // Thiết lập trạng thái dựa trên chế độ hiển thị của Club cho thành viên mới hoàn toàn
        ClubMemberStatus status = ClubMemberStatus.ACTIVE;
        LocalDateTime joinedAt = LocalDateTime.now();

        if (club.getVisibility() == ClubVisibility.PRIVATE) {
            status = ClubMemberStatus.PENDING;
            joinedAt = null;
        }

        ClubMember newMember = ClubMember.builder()
                .club(club)
                .user(user)
                .role(ClubMemberRole.MEMBER)
                .status(status)
                .joinedAt(joinedAt)
                .build();

        return clubMemberRepository.save(newMember);
    }

    /**
     * Lấy danh sách thành viên chờ duyệt (Chỉ dành cho chủ nhiệm).
     */
    public List<ClubMember> getPendingMembers(Integer clubId, Integer requesterUserId) {
        checkPresidentPrivilege(clubId, requesterUserId);

        return clubMemberRepository.findByClubId(clubId).stream()
                .filter(member -> member.getStatus() == ClubMemberStatus.PENDING)
                .toList();
    }

    /**
     * Phê duyệt hoặc từ chối thành viên (Chỉ dành cho chủ nhiệm).
     */
    @Transactional
    public ClubMember approveMember(Integer clubId, Integer memberId, Integer requesterUserId, boolean approve) {
        checkPresidentPrivilege(clubId, requesterUserId);

        ClubMember member = clubMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thông tin đăng ký thành viên!"));

        if (!member.getClub().getId().equals(clubId)) {
            throw new RuntimeException("Thành viên này không thuộc câu lạc bộ này!");
        }

        if (member.getStatus() != ClubMemberStatus.PENDING) {
            throw new RuntimeException("Thành viên này không ở trạng thái chờ duyệt!");
        }

        if (approve) {
            member.setStatus(ClubMemberStatus.ACTIVE);
            member.setJoinedAt(LocalDateTime.now());
            return clubMemberRepository.save(member);
        } else {
            // Đổi trạng thái thành LEFT để giữ lịch sử và cho phép gửi lại yêu cầu xin tham gia ở lần sau
            member.setStatus(ClubMemberStatus.LEFT);
            member.setJoinedAt(null);
            return clubMemberRepository.save(member);
        }
    }

    /**
     * Lấy danh sách các thành viên đang hoạt động của câu lạc bộ.
     */
    public List<ClubMember> getClubMembers(Integer clubId) {
        if (!clubRepository.existsById(clubId)) {
            throw new RuntimeException("Không tìm thấy Câu lạc bộ!");
        }
        return clubMemberRepository.findByClubId(clubId).stream()
                .filter(member -> member.getStatus() == ClubMemberStatus.ACTIVE)
                .toList();
    }

    private void checkPresidentPrivilege(Integer clubId, Integer requesterUserId) {
        ClubMember requester = clubMemberRepository.findByClubIdAndUserUserId(clubId, requesterUserId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải là thành viên của câu lạc bộ này!"));

        if (requester.getRole() != ClubMemberRole.PRESIDENT || requester.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new RuntimeException("Chỉ chủ nhiệm câu lạc bộ mới có quyền thực hiện hành động này!");
        }
    }

    /**
     * Cập nhật vai trò và phòng ban của thành viên (Chỉ chủ nhiệm mới được làm).
     */
    @Transactional
    public ClubMember updateMemberRoleDept(Integer clubId, Integer memberId, Integer requesterUserId, UpdateMemberRoleDeptRequest request) {
        checkPresidentPrivilege(clubId, requesterUserId);

        ClubMember member = clubMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên!"));

        if (!member.getClub().getId().equals(clubId)) {
            throw new RuntimeException("Thành viên này không thuộc câu lạc bộ này!");
        }

        // Cập nhật phòng ban
        if (request.getDepartmentId() == null || request.getDepartmentId() == 0) {
            // Hủy gán trưởng ban cũ nếu vai trò hiện tại là DEPARTMENT_HEAD và đang là head của phòng ban hiện tại
            if (member.getRole() == ClubMemberRole.DEPARTMENT_HEAD && member.getDepartment() != null) {
                Department oldDept = member.getDepartment();
                if (oldDept.getHead() != null && oldDept.getHead().getId().equals(member.getId())) {
                    oldDept.setHead(null);
                    departmentRepository.save(oldDept);
                }
            }
            member.setDepartment(null);
        } else {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban!"));
            if (!department.getClub().getId().equals(clubId)) {
                throw new RuntimeException("Phòng ban không thuộc câu lạc bộ này!");
            }
            // Hủy gán trưởng ban ở phòng ban cũ nếu chuyển phòng ban
            if (member.getRole() == ClubMemberRole.DEPARTMENT_HEAD && member.getDepartment() != null && !member.getDepartment().getId().equals(department.getId())) {
                Department oldDept = member.getDepartment();
                if (oldDept.getHead() != null && oldDept.getHead().getId().equals(member.getId())) {
                    oldDept.setHead(null);
                    departmentRepository.save(oldDept);
                }
            }
            member.setDepartment(department);
        }

        // Cập nhật vai trò
        if (request.getRole() != null) {
            try {
                ClubMemberRole newRole = ClubMemberRole.valueOf(request.getRole().toUpperCase());

                // Nếu chuyển từ trưởng ban sang vai trò khác, xóa vai trò trưởng ban ở phòng ban
                if (member.getRole() == ClubMemberRole.DEPARTMENT_HEAD && newRole != ClubMemberRole.DEPARTMENT_HEAD && member.getDepartment() != null) {
                    Department dept = member.getDepartment();
                    if (dept.getHead() != null && dept.getHead().getId().equals(member.getId())) {
                        dept.setHead(null);
                        departmentRepository.save(dept);
                    }
                }

                member.setRole(newRole);

                // Nếu vai trò mới là DEPARTMENT_HEAD và đã được gán phòng ban, đặt làm head của phòng ban đó
                if (newRole == ClubMemberRole.DEPARTMENT_HEAD && member.getDepartment() != null) {
                    Department dept = member.getDepartment();
                    if (dept.getHead() != null && !dept.getHead().getId().equals(member.getId())) {
                        ClubMember oldHead = dept.getHead();
                        if (oldHead.getRole() != ClubMemberRole.PRESIDENT) {
                            oldHead.setRole(ClubMemberRole.MEMBER);
                            clubMemberRepository.save(oldHead);
                        }
                    }
                    dept.setHead(member);
                    departmentRepository.save(dept);
                }
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Vai trò không hợp lệ (hợp lệ: PRESIDENT, DEPARTMENT_HEAD, TREASURER, MEMBER)");
            }
        }

        return clubMemberRepository.save(member);
    }

    /**
     * Tạm khóa thành viên (Chủ nhiệm chuyển trạng thái User thành INACTIVE).
     */
    @Transactional
    public void lockMember(Integer clubId, Integer memberId, Integer requesterUserId, boolean lock) {
        checkPresidentPrivilege(clubId, requesterUserId);

        ClubMember member = clubMemberRepository.findById(memberId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên!"));

        if (!member.getClub().getId().equals(clubId)) {
            throw new RuntimeException("Thành viên này không thuộc câu lạc bộ này!");
        }

        User user = member.getUser();
        if (user == null) {
            throw new RuntimeException("Không tìm thấy thông tin người dùng tương ứng!");
        }

        user.setUserStatus(lock ? "INACTIVE" : "ACTIVE");
        userRepository.save(user);
    }
}

