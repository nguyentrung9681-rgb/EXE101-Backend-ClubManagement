package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.Department;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.DepartmentRepository;
import com.example.clubmanagement.Repository.ChatMessageRepository;
import com.example.clubmanagement.dto.DepartmentRequest;
import com.example.clubmanagement.dto.DepartmentResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ChatMessageRepository chatMessageRepository;

    public DepartmentService(DepartmentRepository departmentRepository,
                             ClubRepository clubRepository,
                             ClubMemberRepository clubMemberRepository,
                             ChatMessageRepository chatMessageRepository) {
        this.departmentRepository = departmentRepository;
        this.clubRepository = clubRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    /**
     * Tạo phòng ban mới. Chỉ PRESIDENT mới có quyền.
     */
    @Transactional
    public DepartmentResponse createDepartment(Integer clubId, DepartmentRequest request, Integer requesterUserId) {
        checkPresidentPrivilege(clubId, requesterUserId);

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));

        Department department = Department.builder()
                .name(request.getName())
                .description(request.getDescription())
                .club(club)
                .build();

        // Lưu trước để có ID
        department = departmentRepository.save(department);

        if (request.getHeadMemberId() != null && request.getHeadMemberId() > 0) {
            ClubMember headMember = clubMemberRepository.findById(request.getHeadMemberId())
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên làm trưởng ban!"));

            if (!headMember.getClub().getId().equals(clubId)) {
                throw new RuntimeException("Thành viên trưởng ban không thuộc câu lạc bộ này!");
            }

            // Gán vai trò trưởng ban và thuộc phòng ban này
            headMember.setRole(ClubMemberRole.DEPARTMENT_HEAD);
            headMember.setDepartment(department);
            clubMemberRepository.save(headMember);

            department.setHead(headMember);
            department = departmentRepository.save(department);
        }

        return mapToDepartmentResponse(department);
    }

    /**
     * Lấy danh sách phòng ban trong câu lạc bộ. Chỉ thành viên trong CLB mới được xem.
     */
    public List<DepartmentResponse> getDepartments(Integer clubId, Integer requesterUserId) {
        ClubMember requester = clubMemberRepository.findByClubIdAndUserUserId(clubId, requesterUserId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải là thành viên của câu lạc bộ này!"));

        if (requester.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new RuntimeException("Tài khoản thành viên của bạn chưa hoạt động trong câu lạc bộ này!");
        }

        return departmentRepository.findByClubId(clubId).stream()
                .map(this::mapToDepartmentResponse)
                .collect(Collectors.toList());
    }

    /**
     * Cập nhật thông tin phòng ban. Chỉ PRESIDENT mới có quyền.
     */
    @Transactional
    public DepartmentResponse updateDepartment(Integer clubId, Integer departmentId, DepartmentRequest request, Integer requesterUserId) {
        checkPresidentPrivilege(clubId, requesterUserId);

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban!"));

        if (!department.getClub().getId().equals(clubId)) {
            throw new RuntimeException("Phòng ban này không thuộc câu lạc bộ chỉ định!");
        }

        if (request.getName() != null) {
            department.setName(request.getName());
        }
        if (request.getDescription() != null) {
            department.setDescription(request.getDescription());
        }

        // Xử lý thay đổi trưởng ban
        if (request.getHeadMemberId() != null) {
            if (request.getHeadMemberId() == 0) {
                // Xóa trưởng ban hiện tại
                if (department.getHead() != null) {
                    ClubMember oldHead = department.getHead();
                    if (oldHead.getRole() != ClubMemberRole.PRESIDENT) {
                        oldHead.setRole(ClubMemberRole.MEMBER); // Chuyển về member thường
                        clubMemberRepository.save(oldHead);
                    }
                }
                department.setHead(null);
            } else {
                ClubMember newHead = clubMemberRepository.findById(request.getHeadMemberId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy thành viên để gán làm trưởng ban!"));

                if (!newHead.getClub().getId().equals(clubId)) {
                    throw new RuntimeException("Thành viên được gán không thuộc câu lạc bộ này!");
                }

                // Nếu có trưởng ban cũ, chuyển về MEMBER thường
                if (department.getHead() != null && !department.getHead().getId().equals(newHead.getId())) {
                    ClubMember oldHead = department.getHead();
                    if (oldHead.getRole() != ClubMemberRole.PRESIDENT) {
                        oldHead.setRole(ClubMemberRole.MEMBER);
                        clubMemberRepository.save(oldHead);
                    }
                }

                // Cập nhật trưởng ban mới
                newHead.setRole(ClubMemberRole.DEPARTMENT_HEAD);
                newHead.setDepartment(department);
                clubMemberRepository.save(newHead);

                department.setHead(newHead);
            }
        }

        department = departmentRepository.save(department);
        return mapToDepartmentResponse(department);
    }

    /**
     * Xóa phòng ban. Chỉ chủ nhiệm câu lạc bộ (PRESIDENT) mới có quyền xóa.
     */
    @Transactional
    public void deleteDepartment(Integer clubId, Integer departmentId, Integer requesterUserId) {
        checkPresidentPrivilege(clubId, requesterUserId);

        Department department = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban!"));

        if (!department.getClub().getId().equals(clubId)) {
            throw new RuntimeException("Phòng ban này không thuộc câu lạc bộ chỉ định!");
        }

        // 1. Gỡ thông tin Trưởng ban (nếu có) để tránh ràng buộc khóa ngoại vòng
        if (department.getHead() != null) {
            ClubMember head = department.getHead();
            if (head.getRole() == ClubMemberRole.DEPARTMENT_HEAD) {
                head.setRole(ClubMemberRole.MEMBER);
                clubMemberRepository.save(head);
            }
            department.setHead(null);
            departmentRepository.save(department);
        }

        // 2. Cập nhật các thành viên thuộc phòng ban này thành không thuộc phòng ban nào (department = null)
        List<ClubMember> membersInDept = clubMemberRepository.findByDepartmentId(departmentId);
        for (ClubMember member : membersInDept) {
            member.setDepartment(null);
            if (member.getRole() == ClubMemberRole.DEPARTMENT_HEAD) {
                member.setRole(ClubMemberRole.MEMBER);
            }
            clubMemberRepository.save(member);
        }

        // 3. Xóa các tin nhắn chat trong phòng ban
        try {
            chatMessageRepository.deleteByDepartmentId(departmentId);
        } catch (Exception e) {
            System.err.println("Lỗi khi xóa tin nhắn chat phòng ban: " + e.getMessage());
        }

        // 4. Xóa phòng ban khỏi cơ sở dữ liệu
        departmentRepository.delete(department);
    }

    /**
     * Kiểm tra quyền truy cập phòng ban (không gian chat).
     */
    public boolean checkChatAccess(Integer clubId, Integer departmentId, Integer userId) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElse(null);

        if (member == null || member.getStatus() != ClubMemberStatus.ACTIVE) {
            return false;
        }

        // Chỉ thành viên thuộc đúng phòng ban mới được vào chat
        return member.getDepartment() != null && member.getDepartment().getId().equals(departmentId);
    }

    private void checkPresidentPrivilege(Integer clubId, Integer requesterUserId) {
        ClubMember requester = clubMemberRepository.findByClubIdAndUserUserId(clubId, requesterUserId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải là thành viên của câu lạc bộ này!"));

        if (requester.getRole() != ClubMemberRole.PRESIDENT || requester.getStatus() != ClubMemberStatus.ACTIVE) {
            throw new RuntimeException("Chỉ chủ nhiệm câu lạc bộ mới có quyền thực hiện hành động này!");
        }
    }

    private DepartmentResponse mapToDepartmentResponse(Department department) {
        if (department == null) return null;
        return DepartmentResponse.builder()
                .id(department.getId())
                .name(department.getName())
                .description(department.getDescription())
                .clubId(department.getClub().getId())
                .headMemberId(department.getHead() != null ? department.getHead().getId() : null)
                .headName(department.getHead() != null && department.getHead().getUser() != null ?
                        department.getHead().getUser().getFullName() : null)
                .createdAt(department.getCreatedAt())
                .build();
    }
}
