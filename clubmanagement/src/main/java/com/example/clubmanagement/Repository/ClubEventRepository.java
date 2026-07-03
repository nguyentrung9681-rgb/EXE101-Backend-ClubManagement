package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ClubEventRepository extends JpaRepository<ClubEvent, Integer> {
    List<ClubEvent> findByClubId(Long clubId);
}
