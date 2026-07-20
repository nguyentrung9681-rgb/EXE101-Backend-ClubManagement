package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubTaskRepository extends JpaRepository<ClubTask, Integer> {
    List<ClubTask> findByClubId(Integer clubId);
    List<ClubTask> findByAssignedUserUserId(Integer userId);
    List<ClubTask> findByClubIdAndIsFinanceRelatedTrue(Integer clubId);
    List<ClubTask> findByEventId(Integer eventId);
    Optional<ClubTask> findByTrelloCardId(String trelloCardId);
}
