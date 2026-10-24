package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.FundContribution;
import com.example.clubmanagement.Enum.ContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FundContributionRepository extends JpaRepository<FundContribution, Integer> {
    List<FundContribution> findByCampaignId(Integer campaignId);
    List<FundContribution> findByCampaignIdAndStatus(Integer campaignId, ContributionStatus status);
    Optional<FundContribution> findByCampaignIdAndUserUserId(Integer campaignId, Integer userId);
    List<FundContribution> findByUserUserId(Integer userId);

    @Query("SELECT c FROM FundContribution c " +
            "JOIN FETCH c.user LEFT JOIN FETCH c.campaign " +
            "WHERE c.campaign.id = :campaignId")
    List<FundContribution> findByCampaignIdWithUser(@Param("campaignId") Integer campaignId);

    @Query("SELECT c FROM FundContribution c " +
            "JOIN FETCH c.user JOIN FETCH c.campaign " +
            "WHERE c.campaign.id = :campaignId AND c.status = :status")
    List<FundContribution> findByCampaignIdAndStatusWithUser(@Param("campaignId") Integer campaignId,
                                                            @Param("status") ContributionStatus status);

    /**
     * Đếm tổng và đã đóng của nhiều chiến dịch trong 1 câu GROUP BY,
     * thay vì SELECT toàn bộ đóng góp của từng chiến dịch.
     * Kết quả: [campaignId, totalMembers, paidMembers]
     */
    @Query("SELECT c.campaign.id, COUNT(c), " +
            "SUM(CASE WHEN c.status = :paidStatus THEN 1 ELSE 0 END) " +
            "FROM FundContribution c WHERE c.campaign.id IN :campaignIds GROUP BY c.campaign.id")
    List<Object[]> aggregateStatsByCampaignIds(@Param("campaignIds") Collection<Integer> campaignIds,
                                               @Param("paidStatus") ContributionStatus paidStatus);
}
