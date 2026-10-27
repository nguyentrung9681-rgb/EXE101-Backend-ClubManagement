package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.PaymentOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, Integer> {
    Optional<PaymentOrder> findByOrderCode(Long orderCode);
    boolean existsByOrderCode(Long orderCode);
    List<PaymentOrder> findByClubIdOrderByCreatedAtDesc(Integer clubId);
    List<PaymentOrder> findByClubIdAndStatus(Integer clubId, com.example.clubmanagement.Enum.OrderStatus status);

    /**
     * Chỉ những đơn PENDING còn trong phiên checkout (mới tạo) đáng để gọi PayOS kiểm tra lại.
     * Giới hạn số lượng để API đọc không bị treo theo số đơn tồn đọng.
     */
    List<PaymentOrder> findTop3ByClubIdAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
            Integer clubId, com.example.clubmanagement.Enum.OrderStatus status, LocalDateTime after);

    @Query("SELECT p FROM PaymentOrder p WHERE p.user.userId = :userId ORDER BY p.createdAt DESC")
    List<PaymentOrder> findByUserIdOrderByCreatedAtDesc(@Param("userId") Integer userId);

    void deleteByClubId(Integer clubId);
}
