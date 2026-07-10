package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubDocument;
import com.example.clubmanagement.Enum.DocumentType;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubDocumentRepository extends JpaRepository<ClubDocument, Integer> {
    List<ClubDocument> findByClubId(Integer clubId);
    List<ClubDocument> findByEventId(Integer eventId);
    Optional<ClubDocument> findByGoogleDocumentId(String googleDocumentId);
    Optional<ClubDocument> findByWebhookChannelIdAndWebhookResourceId(String channelId, String resourceId);

    @Query("SELECT d FROM ClubDocument d WHERE d.club.id = :clubId " +
           "AND (CAST(:search AS string) IS NULL OR LOWER(d.title) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')) OR LOWER(d.contentSummary) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))) " +
           "AND (:documentType IS NULL OR d.documentType = :documentType)")
    List<ClubDocument> findClubDocumentsFiltered(
            @Param("clubId") Integer clubId,
            @Param("search") String search,
            @Param("documentType") DocumentType documentType,
            Sort sort
    );
}

