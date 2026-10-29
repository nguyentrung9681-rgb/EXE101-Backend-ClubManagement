package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ClubEventRepository extends JpaRepository<ClubEvent, Integer> {
    List<ClubEvent> findByClubId(Integer clubId);
    List<ClubEvent> findByStartTimeBetween(LocalDateTime start, LocalDateTime end);
    List<ClubEvent> findByClubIdAndStartTimeBetween(Integer clubId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT e FROM ClubEvent e " +
            "LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.club " +
            "WHERE e.club.id = :clubId")
    List<ClubEvent> findByClubIdWithDetails(@Param("clubId") Integer clubId);

    @Query("SELECT e FROM ClubEvent e " +
            "LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.club " +
            "WHERE e.club.id = :clubId AND e.startTime BETWEEN :start AND :end")
    List<ClubEvent> findByClubIdAndStartTimeBetweenWithDetails(@Param("clubId") Integer clubId,
                                                               @Param("start") LocalDateTime start,
                                                               @Param("end") LocalDateTime end);

    @Query("SELECT e FROM ClubEvent e " +
            "LEFT JOIN FETCH e.createdBy LEFT JOIN FETCH e.club " +
            "WHERE e.startTime BETWEEN :start AND :end")
    List<ClubEvent> findByStartTimeBetweenWithDetails(@Param("start") LocalDateTime start,
                                                      @Param("end") LocalDateTime end);

    /**
     * Chuyển hàng loạt sự kiện đã qua sang ENDED bằng 1 câu UPDATE,
     * thay vì load từng entity và save() trong luồng đọc.
     */
    @Modifying
    @Query("UPDATE ClubEvent e SET e.status = 'ENDED', e.updatedAt = :now " +
            "WHERE e.status = 'ONGOING' AND e.endTime < :now")
    int markPassedEventsEnded(@Param("now") LocalDateTime now);

    long countByClubId(Integer clubId);

    @Query("SELECT ce.club.id, COUNT(ce) FROM ClubEvent ce GROUP BY ce.club.id")
    List<Object[]> countEventsGroupedByClubId();

    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT e FROM ClubEvent e LEFT JOIN FETCH e.createdBy ORDER BY e.createdAt DESC")
    List<ClubEvent> findRecentEvents(Pageable pageable);
}
