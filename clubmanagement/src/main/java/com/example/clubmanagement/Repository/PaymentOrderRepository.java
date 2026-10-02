package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Integer> {
    Optional<PaymentOrder> findByOrderCode(Long orderCode);
    List<PaymentOrder> findByClubIdOrderByCreatedAtDesc(Integer clubId);

    @Query("SELECT p FROM PaymentOrder p WHERE p.user.userId = :userId ORDER BY p.createdAt DESC")
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);
}
