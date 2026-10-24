package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.FundCampaign;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FundCampaignRepository extends JpaRepository<FundCampaign, Integer> {
    List<FundCampaign> findByClubIdOrderByCreatedAtDesc(Integer clubId);

    /** Nạp sẵn người tạo để khỏi phát sinh query per-campaign khi map DTO. */
    @EntityGraph(attributePaths = "createdBy")
    List<FundCampaign> getByClubIdOrderByCreatedAtDesc(Integer clubId);
}
