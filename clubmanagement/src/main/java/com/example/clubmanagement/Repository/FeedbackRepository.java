package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Integer> {

    @EntityGraph(attributePaths = "user")
    Optional<Feedback> findByUser_UserId(Integer userId);

    boolean existsByUser_UserId(Integer userId);

    /** Danh sách cho admin - fetch luôn user để tránh N+1. */
    @EntityGraph(attributePaths = "user")
    Page<Feedback> findAllBy(Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Page<Feedback> findByRating(Short rating, Pageable pageable);

    @Query("SELECT AVG(f.rating) FROM Feedback f")
    Double findAverageRating();

    /** Mỗi phần tử: [rating (Short), count (Long)] */
    @Query("SELECT f.rating, COUNT(f) FROM Feedback f GROUP BY f.rating")
    List<Object[]> countGroupByRating();
}
