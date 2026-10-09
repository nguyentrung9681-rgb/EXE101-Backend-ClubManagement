package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.*;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.FinanceDtos.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class FinanceService {

    private final FinancialTransactionRepository txRepo;
    private final ClubRepository clubRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public FinanceSummaryResponse getSummary(Integer clubId) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfThisMonth = YearMonth.now().atDay(1).atStartOfDay();
        LocalDateTime startOfLastMonth = YearMonth.now().minusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime endOfLastMonth = startOfThisMonth.minusNanos(1);

        BigDecimal currentMonthIncome = safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.INCOME, startOfThisMonth, now));
        BigDecimal lastMonthIncome = safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.INCOME, startOfLastMonth, endOfLastMonth));

        BigDecimal currentMonthExpense = safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.EXPENSE, startOfThisMonth, now));
        BigDecimal lastMonthExpense = safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.EXPENSE, startOfLastMonth, endOfLastMonth));

        BigDecimal totalIncomeAllTime = safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.INCOME, LocalDateTime.of(2000, 1, 1, 0, 0), now));
        BigDecimal totalExpenseAllTime = safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.EXPENSE, LocalDateTime.of(2000, 1, 1, 0, 0), now));

        Double incomeGrowth = calculateGrowth(currentMonthIncome, lastMonthIncome);
        Double expenseGrowth = calculateGrowth(currentMonthExpense, lastMonthExpense);

        return FinanceSummaryResponse.builder()
                .currentBalance(totalIncomeAllTime.subtract(totalExpenseAllTime))
                .totalIncome(currentMonthIncome)
                .incomeGrowthPercentage(incomeGrowth)
                .totalExpense(currentMonthExpense)
                .expenseGrowthPercentage(expenseGrowth)
                .pendingTransactionsCount(txRepo.countByClubIdAndStatus(clubId, TransactionStatus.PENDING))
                .build();
    }

    private BigDecimal safeSum(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private Double calculateGrowth(BigDecimal current, BigDecimal previous) {
        if (previous.compareTo(BigDecimal.ZERO) == 0) {
            return current.compareTo(BigDecimal.ZERO) > 0 ? 100.0 : 0.0;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .doubleValue();
    }

    @Transactional(readOnly = true)
    public ChartOverviewResponse getOverviewChart(Integer clubId, int months) {
        List<String> labels = new ArrayList<>();
        List<BigDecimal> incomeData = new ArrayList<>();
        List<BigDecimal> expenseData = new ArrayList<>();

        YearMonth current = YearMonth.now();
        for (int i = months - 1; i >= 0; i--) {
            YearMonth targetMonth = current.minusMonths(i);
            labels.add(targetMonth.getMonth().name().substring(0, 3));

            LocalDateTime start = targetMonth.atDay(1).atStartOfDay();
            LocalDateTime end = targetMonth.atEndOfMonth().atTime(23, 59, 59);

            incomeData.add(safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.INCOME, start, end)));
            expenseData.add(safeSum(txRepo.sumAmountByTypeAndDateRange(clubId, TransactionType.EXPENSE, start, end)));
        }

        return ChartOverviewResponse.builder()
                .labels(labels)
                .incomeData(incomeData)
                .expenseData(expenseData)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CategoryBreakdownResponse> getCategoryBreakdown(Integer clubId) {
        List<Object[]> rawData = txRepo.getExpenseBreakdownByCategory(clubId);
        BigDecimal totalExpense = rawData.stream()
                .map(r -> r[1] != null ? (BigDecimal) r[1] : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CategoryBreakdownResponse> res = new ArrayList<>();
        for (Object[] row : rawData) {
            TransactionCategory cat = (TransactionCategory) row[0];
            BigDecimal amount = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            double pct = totalExpense.compareTo(BigDecimal.ZERO) > 0 ?
                    amount.divide(totalExpense, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue() : 0.0;
            res.add(CategoryBreakdownResponse.builder()
                    .category(cat)
                    .totalAmount(amount)
                    .percentage(pct)
                    .build());
        }
        return res;
    }

    @Transactional
    public TransactionResponse createTransaction(Integer clubId, Integer userId, TransactionRequest req) {
        Club club = clubRepo.findById(clubId).orElseThrow(() -> new RuntimeException("Club not found"));
        User user = userId != null ? userRepo.findById(userId).orElse(null) : null;

        FinancialTransaction tx = FinancialTransaction.builder()
                .club(club)
                .createdBy(user)
                .description(req.getDescription())
                .type(req.getType())
                .amount(req.getAmount())
                .category(req.getCategory())
                .status(req.getStatus() != null ? req.getStatus() : TransactionStatus.COMPLETED)
                .build();

        return mapToDto(txRepo.save(tx));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(Integer clubId, String search, TransactionType type,
                                                     TransactionCategory category, TransactionStatus status,
                                                     Pageable pageable) {
        return txRepo.searchTransactions(clubId, search, type, category, status, pageable).map(this::mapToDto);
    }

    @Transactional
    public TransactionResponse updateStatus(Integer txId, TransactionStatus status) {
        FinancialTransaction tx = txRepo.findById(txId).orElseThrow(() -> new RuntimeException("Tx not found"));
        tx.setStatus(status);
        return mapToDto(txRepo.save(tx));
    }

    @Transactional
    public void deleteTransaction(Integer txId) {
        txRepo.deleteById(txId);
    }

    public byte[] exportTransactionsToExcel(Integer clubId) throws IOException {
        List<FinancialTransaction> list = txRepo.findAllByClubIdOrderByCreatedAtDesc(clubId);
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Transactions");

            Row header = sheet.createRow(0);
            String[] headers = {"ID", "Date", "Description", "Type", "Amount", "Category", "Status"};
            for (int i = 0; i < headers.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(headers[i]);
            }

            int rowIdx = 1;
            for (FinancialTransaction tx : list) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(tx.getId() != null ? tx.getId() : 0);
                row.createCell(1).setCellValue(tx.getCreatedAt() != null ? tx.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE) : "");
                row.createCell(2).setCellValue(tx.getDescription() != null ? tx.getDescription() : "");
                row.createCell(3).setCellValue(tx.getType() != null ? tx.getType().name() : "");
                row.createCell(4).setCellValue(tx.getAmount() != null ? tx.getAmount().doubleValue() : 0.0);
                row.createCell(5).setCellValue(tx.getCategory() != null ? tx.getCategory().name() : "");
                row.createCell(6).setCellValue(tx.getStatus() != null ? tx.getStatus().name() : "");
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private TransactionResponse mapToDto(FinancialTransaction tx) {
        return TransactionResponse.builder()
                .id(tx.getId())
                .clubId(tx.getClub() != null ? tx.getClub().getId() : null)
                .description(tx.getDescription())
                .type(tx.getType())
                .amount(tx.getAmount())
                .category(tx.getCategory())
                .status(tx.getStatus())
                .createdByName(tx.getCreatedBy() != null ? tx.getCreatedBy().getFullName() : "N/A")
                .createdAt(tx.getCreatedAt())
                .build();
    }
}
