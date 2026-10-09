package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.FundContribution;
import com.example.clubmanagement.Enum.ContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface FundContributionRepository extends JpaRepository<FundContribution, Integer> {
    List<FundContribution> findByCampaignId(Integer campaignId);
    List<FundContribution> findByCampaignIdAndStatus(Integer campaignId, ContributionStatus status);
    Optional<FundContribution> findByCampaignIdAndUserUserId(Integer campaignId, Integer userId);
    List<FundContribution> findByUserUserId(Integer userId);
}
