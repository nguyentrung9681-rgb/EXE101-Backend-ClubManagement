package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClubEventRepository extends JpaRepository<ClubEvent, Integer> {
    List<ClubEvent> findByClubId(Integer clubId);
    List<ClubEvent> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<ClubEvent> findByClubIdAndStartTimeBetween(Integer clubId, LocalDateTime start, LocalDateTime end);
}
