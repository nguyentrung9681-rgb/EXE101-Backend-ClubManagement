package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ChatMessageArchive;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageArchiveRepository extends JpaRepository<ChatMessageArchive, Long> {

    @Query("SELECT a FROM ChatMessageArchive a WHERE a.clubId = :clubId AND a.departmentId = :departmentId ORDER BY a.id DESC")
    List<ChatMessageArchive> findArchivedDepartmentMessages(@Param("clubId") Integer clubId,
                                                             @Param("departmentId") Integer departmentId,
                                                             Pageable pageable);

    @Query("SELECT a FROM ChatMessageArchive a WHERE a.clubId = :clubId AND a.departmentId IS NULL ORDER BY a.id DESC")
    List<ChatMessageArchive> findArchivedClubMessages(@Param("clubId") Integer clubId, Pageable pageable);
}
