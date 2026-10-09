package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.FinancialTransaction;
import com.example.clubmanagement.Enum.TransactionCategory;
import com.example.clubmanagement.Enum.TransactionStatus;
import com.example.clubmanagement.Enum.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface FinancialTransactionRepository extends JpaRepository<FinancialTransaction, Integer> {

    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.club.id = :clubId " +
            "AND (:search IS NULL OR LOWER(ft.description) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:type IS NULL OR ft.type = :type) " +
            "AND (:category IS NULL OR ft.category = :category) " +
            "AND (:status IS NULL OR ft.status = :status)")
    Page<FinancialTransaction> searchTransactions(
            @Param("clubId") Integer clubId,
            @Param("search") String search,
            @Param("type") TransactionType type,
            @Param("category") TransactionCategory category,
            @Param("status") TransactionStatus status,
            Pageable pageable
    );

    List<FinancialTransaction> findAllByClubIdOrderByCreatedAtDesc(Integer clubId);

    @Query("SELECT SUM(ft.amount) FROM FinancialTransaction ft " +
            "WHERE ft.club.id = :clubId AND ft.status = 'COMPLETED' AND ft.type = :type " +
            "AND ft.createdAt BETWEEN :from AND :to")
    BigDecimal sumAmountByTypeAndDateRange(@Param("clubId") Integer clubId,
                                           @Param("type") TransactionType type,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    long countByClubIdAndStatus(Integer clubId, TransactionStatus status);

    @Query("SELECT ft.category, SUM(ft.amount) FROM FinancialTransaction ft " +
            "WHERE ft.club.id = :clubId AND ft.status = 'COMPLETED' AND ft.type = 'EXPENSE' " +
            "GROUP BY ft.category")
    List<Object[]> getExpenseBreakdownByCategory(@Param("clubId") Integer clubId);

    @Query("SELECT ft FROM FinancialTransaction ft WHERE ft.club.id = :clubId " +
            "AND ft.status = 'COMPLETED' AND ft.createdAt >= :since ORDER BY ft.createdAt ASC")
    List<FinancialTransaction> findCompletedSince(@Param("clubId") Integer clubId, @Param("since") LocalDateTime since);
}
