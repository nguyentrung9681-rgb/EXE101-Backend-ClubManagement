package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubSubscription;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubSubscriptionRepository extends JpaRepository<ClubSubscription, Integer> {
    @EntityGraph(attributePaths = {"club", "subscriptionPackage"})
    Optional<ClubSubscription> findFirstByClubIdAndStatusOrderByEndDateDesc(Integer clubId, SubscriptionStatus status);

    /** Toàn bộ gói đang hiệu lực, tải kèm package + đơn hàng để thống kê không phát sinh N+1. */
    @EntityGraph(attributePaths = {"subscriptionPackage", "order"})
    List<ClubSubscription> findByStatus(SubscriptionStatus status);

    List<ClubSubscription> findByClubIdOrderByCreatedAtDesc(Integer clubId);
    void deleteByClubId(Integer clubId);
}
