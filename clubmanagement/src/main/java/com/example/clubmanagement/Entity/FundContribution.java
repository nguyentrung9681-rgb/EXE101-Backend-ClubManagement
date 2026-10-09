package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.ContributionStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "fund_contributions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FundContribution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "campaign_id", nullable = false)
    private FundCampaign campaign;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    private ContributionStatus status;

    private String proofImageUrl;
    private LocalDateTime paidAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
