package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {
    boolean existsByUserUserIdAndClubId(Integer userId, Long clubId);
    Optional<ClubMember> findByClubIdAndUserUserId(Long clubId, Integer userId);
}
