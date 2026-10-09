package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.FundCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FundCampaignRepository extends JpaRepository<FundCampaign, Integer> {
    List<FundCampaign> findByClubIdOrderByCreatedAtDesc(Integer clubId);
}
