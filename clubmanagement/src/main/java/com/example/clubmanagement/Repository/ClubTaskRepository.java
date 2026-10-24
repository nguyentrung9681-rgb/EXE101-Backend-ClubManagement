package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClubTaskRepository extends JpaRepository<ClubTask, Integer> {
    List<ClubTask> findByClubId(Integer clubId);
    List<ClubTask> findByAssignedUserUserId(Integer userId);
    List<ClubTask> findByClubIdAndIsFinanceRelatedTrue(Integer clubId);
    List<ClubTask> findByEventId(Integer eventId);
    Optional<ClubTask> findByTrelloCardId(String trelloCardId);
    void deleteByClubId(Integer clubId);

    // ─── Danh sách task: nạp sẵn club/event/assignedUser + lọc bằng SQL thay vì trong RAM ───

    @Query("SELECT t FROM ClubTask t " +
            "LEFT JOIN FETCH t.club LEFT JOIN FETCH t.event LEFT JOIN FETCH t.assignedUser " +
            "WHERE t.club.id = :clubId " +
            "AND (:department IS NULL OR LOWER(t.department) = LOWER(:department)) " +
            "AND (:status IS NULL OR LOWER(t.status) = LOWER(:status))")
    List<ClubTask> findInClubWithDetails(@Param("clubId") Integer clubId,
                                        @Param("department") String department,
                                        @Param("status") String status);

    @Query("SELECT t FROM ClubTask t " +
            "LEFT JOIN FETCH t.club LEFT JOIN FETCH t.event LEFT JOIN FETCH t.assignedUser " +
            "WHERE t.club.id = :clubId AND t.assignedUser.userId = :userId " +
            "AND (:department IS NULL OR LOWER(t.department) = LOWER(:department)) " +
            "AND (:status IS NULL OR LOWER(t.status) = LOWER(:status))")
    List<ClubTask> findAssignedToUserInClubWithDetails(@Param("clubId") Integer clubId,
                                                      @Param("userId") Integer userId,
                                                      @Param("department") String department,
                                                      @Param("status") String status);

    @Query("SELECT t FROM ClubTask t " +
            "LEFT JOIN FETCH t.club LEFT JOIN FETCH t.event LEFT JOIN FETCH t.assignedUser " +
            "WHERE t.club.id = :clubId AND t.isFinanceRelated = true " +
            "AND (:department IS NULL OR LOWER(t.department) = LOWER(:department)) " +
            "AND (:status IS NULL OR LOWER(t.status) = LOWER(:status))")
    List<ClubTask> findFinanceInClubWithDetails(@Param("clubId") Integer clubId,
                                                @Param("department") String department,
                                                @Param("status") String status);

    @Query("SELECT t FROM ClubTask t " +
            "LEFT JOIN FETCH t.club LEFT JOIN FETCH t.event LEFT JOIN FETCH t.assignedUser " +
            "WHERE t.club.id = :clubId AND t.event.id = :eventId " +
            "AND (:department IS NULL OR LOWER(t.department) = LOWER(:department)) " +
            "AND (:status IS NULL OR LOWER(t.status) = LOWER(:status))")
    List<ClubTask> findInEventWithDetails(@Param("clubId") Integer clubId,
                                         @Param("eventId") Integer eventId,
                                         @Param("department") String department,
                                         @Param("status") String status);

    /**
     * Thống kê hiệu suất theo từng thành viên chỉ bằng 1 câu GROUP BY,
     * thay vì vòng lặp O(thành viên x task) trên RAM.
     * Kết quả: [userId, assigned, completed, inProgress, overdue]
     */
    @Query("SELECT t.assignedUser.userId, COUNT(t), " +
            "SUM(CASE WHEN LOWER(t.status) = 'done' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN LOWER(t.status) = 'doing' THEN 1 ELSE 0 END), " +
            "SUM(CASE WHEN t.dueDate < :now AND LOWER(t.status) <> 'done' THEN 1 ELSE 0 END) " +
            "FROM ClubTask t WHERE t.club.id = :clubId AND t.assignedUser IS NOT NULL " +
            "GROUP BY t.assignedUser.userId")
    List<Object[]> countTasksByMember(@Param("clubId") Integer clubId, @Param("now") LocalDateTime now);
}
