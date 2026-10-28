package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrackingEventRepository extends JpaRepository<TrackingEvent, Long> {

    Optional<TrackingEvent> findFirstByUserIdAndUtmSourceIsNotNullOrderByCreatedAtAsc(Integer userId);

    /**
     * Lay du lieu tho cua phin chuyen doi trong khoang thoi gian:
     * [eventName, sessionId, userId, utmSource, utmMedium, utmCampaign, amount]
     */
    @Query("SELECT t.eventName, t.sessionId, t.userId, t.utmSource, t.utmMedium, t.utmCampaign, t.amount "
            + "FROM TrackingEvent t WHERE t.createdAt BETWEEN :from AND :to ORDER BY t.createdAt ASC")
    List<Object[]> findFunnelRows(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COUNT(t) FROM TrackingEvent t WHERE t.createdAt BETWEEN :from AND :to")
    long countInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
