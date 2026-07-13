package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.DocumentRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRevisionRepository extends JpaRepository<DocumentRevision, Integer> {
    List<DocumentRevision> findByClubDocumentIdOrderByVersionDesc(Integer clubDocumentId);
    Optional<DocumentRevision> findFirstByClubDocumentIdOrderByVersionDesc(Integer clubDocumentId);
}
