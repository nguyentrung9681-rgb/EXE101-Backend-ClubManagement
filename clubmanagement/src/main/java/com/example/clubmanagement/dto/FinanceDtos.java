package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.ContributionStatus;
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

    @Data @Builder
    public static class FundCampaignResponse {
        private Integer id;
        private Integer clubId;
        private String title;
        private BigDecimal amountPerMember;
        private LocalDateTime deadline;
        private String bankAccountInfo;
        private String description;
        private String createdByName;
        private LocalDateTime createdAt;
        private Integer totalMembers;
        private Integer paidMembers;
        private BigDecimal totalCollected;
        private BigDecimal targetAmount;
    }

    @Data @Builder
    public static class FundContributionResponse {
        private Integer id;
        private Integer campaignId;
        private Integer userId;
        private String fullName;
        private String email;
        private String avatarUrl;
        private ContributionStatus status;
        private String proofImageUrl;
        private LocalDateTime paidAt;
        private LocalDateTime updatedAt;
    }
}