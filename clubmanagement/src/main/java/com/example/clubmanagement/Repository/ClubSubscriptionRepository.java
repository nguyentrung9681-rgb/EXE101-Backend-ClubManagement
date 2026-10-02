package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubSubscription;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubSubscriptionRepository extends JpaRepository<ClubSubscription, Integer> {
    Optional<ClubSubscription> findFirstByClubIdAndStatusOrderByEndDateDesc(Integer clubId, SubscriptionStatus status);
    List<ClubSubscription> findByClubIdOrderByCreatedAtDesc(Integer clubId);
}
