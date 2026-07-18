package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubTrelloConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClubTrelloConfigRepository extends JpaRepository<ClubTrelloConfig, Integer> {
    Optional<ClubTrelloConfig> findByClubId(Integer clubId);
}
