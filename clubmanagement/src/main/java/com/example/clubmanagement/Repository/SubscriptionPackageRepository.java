package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.SubscriptionPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionPackageRepository extends JpaRepository<SubscriptionPackage, Integer> {
    Optional<SubscriptionPackage> findByCode(String code);
    List<SubscriptionPackage> findByIsActiveTrue();
}
