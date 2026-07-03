package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubRole;
import com.example.clubmanagement.Enum.DepartmentRole;
import com.example.clubmanagement.Repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class DepartmentService {
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DepartmentMemberRepository departmentMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private ClubMemberRepository clubMemberRepository;

    // Check xem user có phải là Club Manager của CLB đó hay không
    private void validateClubManager(Integer userId, Integer clubId) {
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không thuộc câu lạc bộ này."));
        if (member.getRole() != ClubRole.CLUB_MANAGER) {
            throw new RuntimeException("Chỉ Chủ nhiệm (Club Manager) mới có quyền thực hiện.");
        }
    }

    public Department createDepartment(Integer managerId, Integer clubId, String name, String description, String colorHex) {
        validateClubManager(managerId, clubId);

        Club club = clubRepository.findById(clubId).orElseThrow(() -> new RuntimeException("Không tìm thấy CLB."));
        Department dept = Department.builder()
                .club(club)
                .departmentName(name)
                .description(description)
                .colorHex(colorHex)
                .createdByUserId(managerId)
                .build();
        return departmentRepository.save(dept);
    }

    public DepartmentMember addMemberToDepartment(Integer operatorId, Long departmentId, Integer targetUserId, DepartmentRole role) {
        Department dept = departmentRepository.findById(departmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy phòng ban."));

        // Quyền: Hoặc là Club Manager của CLB, hoặc là Trưởng ban (HEAD) của phòng ban đó
        boolean isClubManager = clubMemberRepository.findByClubIdAndUserUserId(dept.getClub().getId(), operatorId)
                .map(m -> m.getRole() == ClubRole.CLUB_MANAGER).orElse(false);
        boolean isDeptHead = departmentMemberRepository.findByDepartmentIdAndUserUserId (departmentId, operatorId)
                .map(m -> m.getRole() == DepartmentRole.HEAD).orElse(false);

        if (!isClubManager && !isDeptHead) {
            throw new RuntimeException("Bạn không có quyền thêm thành viên vào ban này.");
        }

        if (departmentMemberRepository.existsByDepartmentIdAndUserUserId (departmentId, targetUserId)) {
            throw new RuntimeException("Thành viên này đã ở trong phòng ban rồi.");
        }

        User targetUser = userRepository.findById(targetUserId).orElseThrow(() -> new RuntimeException("Không tìm thấy User."));

        DepartmentMember depMember = DepartmentMember.builder()
                .department(dept)
                .club(dept.getClub())
                .user(targetUser)
                .role(role)
                .status("ACTIVE")
                .addedByUserId(operatorId)
                .joinedAt(LocalDateTime.now())
                .build();
        return departmentMemberRepository.save(depMember);
    }

    public List<DepartmentMember> getMembers(Long departmentId) {
        return departmentMemberRepository.findByDepartmentId(departmentId);
    }
}
