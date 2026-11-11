package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.PaymentOrder;
import com.example.clubmanagement.Enum.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
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

    List<PaymentOrder> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);

    @EntityGraph(attributePaths = {"subscriptionPackage"})
    List<PaymentOrder> findByStatusAndPaidAtBetween(
            com.example.clubmanagement.Enum.OrderStatus status, LocalDateTime from, LocalDateTime to);

    // MANUAL_ cũng có trạng thái PAID nhưng không chứng minh được thanh toán thật.
    @Query("SELECT COUNT(p) FROM PaymentOrder p "
            + "WHERE p.status = com.example.clubmanagement.Enum.OrderStatus.PAID "
            + "AND p.paidAt IS NOT NULL AND p.amount > 0 "
            + "AND p.transactionNo IS NOT NULL AND TRIM(p.transactionNo) <> '' "
            + "AND p.transactionNo NOT LIKE 'MANUAL!_%' ESCAPE '!'")
    long countPaidOrdersForTransactionGoal();

    /** CLB đã có đơn thanh toán trước mốc này, dùng để phân biệt mua mới với gia hạn. */
    @Query("SELECT DISTINCT p.club.id FROM PaymentOrder p "
            + "WHERE p.status = com.example.clubmanagement.Enum.OrderStatus.PAID "
            + "AND p.paidAt < :moment AND p.club IS NOT NULL")
    List<Integer> findClubIdsPaidBefore(@Param("moment") LocalDateTime moment);

    /** Sổ giao dịch cho Admin: lọc tại SQL và fetch sẵn quan hệ để không phát sinh N+1 khi phân trang. */
    @EntityGraph(attributePaths = {"user", "club", "subscriptionPackage"})
    @Query("SELECT p FROM PaymentOrder p WHERE "
            + "(:status IS NULL OR p.status = :status) "
            + "AND (:from IS NULL OR p.createdAt >= :from) "
            + "AND (:to IS NULL OR p.createdAt <= :to) "
            + "AND (:search IS NULL OR LOWER(p.user.fullName) LIKE :search ESCAPE '!' "
            + "     OR LOWER(p.user.email) LIKE :search ESCAPE '!' OR LOWER(p.club.name) LIKE :search ESCAPE '!' "
            + "     OR LOWER(p.transactionNo) LIKE :search ESCAPE '!' OR STR(p.orderCode) LIKE :search ESCAPE '!')")
    Page<PaymentOrder> searchForAdmin(@Param("status") OrderStatus status,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to,
                                      @Param("search") String search,
                                      Pageable pageable);

    /** Khách đã từng trả tiền (đếm một lần cho mỗi người), mẫu số của tỉ lệ review và mua lại. */
    @Query("SELECT COUNT(DISTINCT p.user.userId) FROM PaymentOrder p "
            + "WHERE p.status = com.example.clubmanagement.Enum.OrderStatus.PAID AND p.user IS NOT NULL")
    long countDistinctPayingCustomers();

    /** Khách có nhiều hơn một đơn PAID, dùng tính tỉ lệ mua lại (retention). */
    @Query("SELECT p.user.userId FROM PaymentOrder p "
            + "WHERE p.status = com.example.clubmanagement.Enum.OrderStatus.PAID AND p.user IS NOT NULL "
            + "GROUP BY p.user.userId HAVING COUNT(p) > 1")
    List<Integer> findRepeatPayingCustomerIds();

    /** Đơn PAID gần nhất của một người, dùng để gắn review với đơn đã hoàn thành. */
    Optional<PaymentOrder> findFirstByUser_UserIdAndStatusOrderByPaidAtDesc(Integer userId, OrderStatus status);

    void deleteByClubId(Integer clubId);
}
