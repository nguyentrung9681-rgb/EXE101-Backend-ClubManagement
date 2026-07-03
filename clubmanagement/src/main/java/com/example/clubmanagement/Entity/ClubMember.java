package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.ClubRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "club_member")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ClubMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClubRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClubMemberStatus status; // ACTIVE, PENDING, LEFT

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        if (status == null) {
            status = ClubMemberStatus.PENDING;
        }
        if (joinedAt == null && status == ClubMemberStatus.ACTIVE) {
            joinedAt = LocalDateTime.now();
        }
    }
}
