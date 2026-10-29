package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubTrelloConfig;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubTrelloConfigRepository extends JpaRepository<ClubTrelloConfig, Integer> {
    Optional<ClubTrelloConfig> findByClubId(Integer clubId);

    @Query("SELECT t FROM ClubTrelloConfig t LEFT JOIN FETCH t.club c LEFT JOIN FETCH c.createdBy ORDER BY t.createdAt DESC")
    List<ClubTrelloConfig> findRecentConfigs(Pageable pageable);
}
