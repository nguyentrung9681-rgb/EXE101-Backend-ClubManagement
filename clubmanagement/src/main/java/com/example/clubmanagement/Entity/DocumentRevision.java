package com.example.clubmanagement.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_revision")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DocumentRevision {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_document_id", nullable = false)
    private ClubDocument clubDocument;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;
}
