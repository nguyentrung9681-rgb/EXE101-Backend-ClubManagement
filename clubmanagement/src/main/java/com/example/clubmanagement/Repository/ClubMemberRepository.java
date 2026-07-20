package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMember, Integer> {
    Optional<ClubMember> findByClubIdAndUserUserId(Integer clubId, Integer userId);
    List<ClubMember> findByClubId(Integer clubId);
    List<ClubMember> findByUserUserId(Integer userId);

    // ─── Phân quyền theo CLB ───
    /** Tìm thành viên ACTIVE của CLB */
    Optional<ClubMember> findByClubIdAndUserUserIdAndStatus(
            Integer clubId, Integer userId, ClubMemberStatus status);

    /** Kiểm tra thành viên có đúng role và status không */
    Optional<ClubMember> findByClubIdAndUserUserIdAndRoleAndStatus(
            Integer clubId, Integer userId, ClubMemberRole role, ClubMemberStatus status);
}
