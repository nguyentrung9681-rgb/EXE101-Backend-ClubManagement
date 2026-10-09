package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.TransactionCategory;
import com.example.clubmanagement.Enum.TransactionStatus;
import com.example.clubmanagement.Enum.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class FinanceDtos {

    @Data
    public static class TransactionRequest {
        private String description;
        private TransactionType type;
        private BigDecimal amount;
        private TransactionCategory category;
        private TransactionStatus status;
    }

    @Data @Builder
    public static class TransactionResponse {
        private Integer id;
        private Integer clubId;
        private String description;
        private TransactionType type;
        private BigDecimal amount;
        private TransactionCategory category;
        private TransactionStatus status;
        private String createdByName;
        private LocalDateTime createdAt;
    }

    @Data @Builder
    public static class FinanceSummaryResponse {
        private BigDecimal currentBalance;
        private BigDecimal totalIncome;
        private Double incomeGrowthPercentage;
        private BigDecimal totalExpense;
        private Double expenseGrowthPercentage;
        private long pendingTransactionsCount;
    }

    @Data @Builder
    public static class ChartOverviewResponse {
        private List<String> labels;
        private List<BigDecimal> incomeData;
        private List<BigDecimal> expenseData;
    }

    @Data @Builder
    public static class CategoryBreakdownResponse {
        private TransactionCategory category;
        private BigDecimal totalAmount;
        private Double percentage;
    }

    @Data
    public static class CreateCampaignRequest {
        private String title;
        private BigDecimal amountPerMember;
        private LocalDateTime deadline;
        private String bankAccountInfo;
        private String description;
    }
}