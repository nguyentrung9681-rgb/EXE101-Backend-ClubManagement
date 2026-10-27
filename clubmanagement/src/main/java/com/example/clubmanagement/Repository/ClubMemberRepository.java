package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMember, Integer> {
    Optional<ClubMember> findByClubIdAndUserUserId(Integer clubId, Integer userId);
    List<ClubMember> findByClubId(Integer clubId);
    List<ClubMember> findByUserUserId(Integer userId);
    List<ClubMember> findByDepartmentId(Integer departmentId);
    long countByClubIdAndStatus(Integer clubId, ClubMemberStatus status);

    /** Danh sách thành viên theo trạng thái, nạp sẵn user/department/club cho bước map ra DTO. */
    @Query("SELECT cm FROM ClubMember cm " +
            "JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.department " +
            "LEFT JOIN FETCH cm.club " +
            "WHERE cm.club.id = :clubId AND cm.status = :status")
    List<ClubMember> findByClubIdAndStatusWithDetails(@Param("clubId") Integer clubId,
                                                      @Param("status") ClubMemberStatus status);

    @Query("SELECT cm FROM ClubMember cm " +
            "JOIN FETCH cm.user " +
            "LEFT JOIN FETCH cm.department " +
            "JOIN FETCH cm.club " +
            "WHERE cm.user.userId = :userId")
    List<ClubMember> findByUserUserIdWithDetails(@Param("userId") Integer userId);

    /** Chỉ lấy định danh user của thành viên, dùng cho luồng broadcast WebSocket. */
    @Query("SELECT m.user.userId, m.user.username FROM ClubMember m " +
            "WHERE m.club.id = :clubId AND m.status = :status")
    List<Object[]> findUserIdAndUsernameByClubIdAndStatus(@Param("clubId") Integer clubId,
                                                          @Param("status") ClubMemberStatus status);

    @Query("SELECT m.user.userId, m.user.username FROM ClubMember m " +
            "WHERE m.department.id = :departmentId AND m.status = :status")
    List<Object[]> findUserIdAndUsernameByDepartmentIdAndStatus(@Param("departmentId") Integer departmentId,
                                                                @Param("status") ClubMemberStatus status);

    /** userId của các thành viên ACTIVE, dùng cho báo cáo hiệu suất. */
    @Query("SELECT m.user FROM ClubMember m WHERE m.club.id = :clubId AND m.status = :status")
    List<com.example.clubmanagement.Entity.User> findUsersByClubIdAndStatus(@Param("clubId") Integer clubId,
                                                                           @Param("status") ClubMemberStatus status);

    // ─── Phân quyền theo CLB ───
    /** Tìm thành viên ACTIVE của CLB */
    Optional<ClubMember> findByClubIdAndUserUserIdAndStatus(
            Integer clubId, Integer userId, ClubMemberStatus status);

    /** Kiểm tra thành viên có đúng role và status không */
    Optional<ClubMember> findByClubIdAndUserUserIdAndRoleAndStatus(
            Integer clubId, Integer userId, ClubMemberRole role, ClubMemberStatus status);

    long countByClubId(Integer clubId);
}

