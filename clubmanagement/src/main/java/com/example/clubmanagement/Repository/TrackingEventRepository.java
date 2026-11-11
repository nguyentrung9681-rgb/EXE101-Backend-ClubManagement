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

    /**
     * First-touch UTM sớm nhất (mọi thời điểm, không giới hạn cửa sổ) cho mỗi user trong tập.
     * Dùng để quy doanh thu về đúng kênh kể cả khi khách đến từ kỳ trước.
     * Mỗi phần tử: [userId, utmSource, utmMedium, utmCampaign]. Gọi với tập ids không rỗng.
     */
    @Query("SELECT t.userId, t.utmSource, t.utmMedium, t.utmCampaign FROM TrackingEvent t "
            + "WHERE t.userId IN :ids AND t.utmSource IS NOT NULL "
            + "AND t.createdAt = (SELECT MIN(t2.createdAt) FROM TrackingEvent t2 "
            + "                   WHERE t2.userId = t.userId AND t2.utmSource IS NOT NULL)")
    List<Object[]> findFirstTouchByUserIds(@Param("ids") java.util.Collection<Integer> ids);
}
