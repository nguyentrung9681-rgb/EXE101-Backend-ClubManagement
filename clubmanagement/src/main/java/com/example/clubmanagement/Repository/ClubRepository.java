package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Enum.ClubStatus;
import com.example.clubmanagement.Enum.ClubVisibility;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubRepository extends JpaRepository<Club, Integer> {
    /** Nạp sẵn người tạo để danh sách CLB không phát sinh query per-row. */
    @Query("SELECT c FROM Club c LEFT JOIN FETCH c.createdBy ORDER BY c.createdAt DESC")
    List<Club> findAllWithCreatedBy();

    /** API chi tiết cần cả danh sách người được quyền xóa. */
    @Query("SELECT c FROM Club c LEFT JOIN FETCH c.createdBy LEFT JOIN FETCH c.deletionPermittedUsers WHERE c.id = :id")
    Optional<Club> findByIdWithDetails(@Param("id") Integer id);

    @Query("SELECT c FROM Club c LEFT JOIN FETCH c.createdBy ORDER BY c.createdAt DESC")
    List<Club> findRecentClubs(Pageable pageable);

    long countByStatus(ClubStatus status);
    long countByVisibility(ClubVisibility visibility);
}
