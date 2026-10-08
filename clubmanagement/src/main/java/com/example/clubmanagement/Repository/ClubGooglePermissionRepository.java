package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubGooglePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubGooglePermissionRepository extends JpaRepository<ClubGooglePermission, Integer> {

    Optional<ClubGooglePermission> findByClubIdAndUserUserId(Integer clubId, Integer userId);

    List<ClubGooglePermission> findByClubId(Integer clubId);

    void deleteByClubIdAndUserUserId(Integer clubId, Integer userId);

    void deleteByClubId(Integer clubId);
}
