package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.FinancialTransaction;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.TransactionCategory;
import com.example.clubmanagement.Enum.TransactionStatus;
import com.example.clubmanagement.Enum.TransactionType;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.FinancialTransactionRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.FinanceDtos.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceServiceTest {

    @Mock
    private FinancialTransactionRepository txRepo;

    @Mock
    private ClubRepository clubRepo;

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private FinanceService financeService;

    private Club sampleClub;
    private User sampleUser;
    private FinancialTransaction sampleTx;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        sampleClub = Club.builder()
                .id(1)
                .name("CLB Cong Nghe Thông Tin")
                .build();

        sampleUser = User.builder()
                .userId(10)
                .fullName("Nguyen Van A")
                .email("nguyenvana@example.com")
                .build();

        sampleTx = FinancialTransaction.builder()
                .id(100)
                .club(sampleClub)
                .createdBy(sampleUser)
                .description("Thu phi gia nhap")
                .type(TransactionType.INCOME)
                .amount(BigDecimal.valueOf(500000))
                .category(TransactionCategory.EVENTS)
                .status(TransactionStatus.COMPLETED)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Test getSummary trả về đúng các thẻ số liệu tài chính")
    void testGetSummary() {
        when(txRepo.sumAmountByTypeAndDateRange(eq(1), eq(TransactionType.INCOME), any(), any()))
                .thenReturn(BigDecimal.valueOf(1000000));
        when(txRepo.sumAmountByTypeAndDateRange(eq(1), eq(TransactionType.EXPENSE), any(), any()))
                .thenReturn(BigDecimal.valueOf(400000));
        when(txRepo.countByClubIdAndStatus(1, TransactionStatus.PENDING))
                .thenReturn(2L);

        FinanceSummaryResponse summary = financeService.getSummary(1);

        assertNotNull(summary);
        assertEquals(BigDecimal.valueOf(600000), summary.getCurrentBalance());
        assertEquals(BigDecimal.valueOf(1000000), summary.getTotalIncome());
        assertEquals(BigDecimal.valueOf(400000), summary.getTotalExpense());
        assertEquals(2L, summary.getPendingTransactionsCount());
    }

    @Test
    @DisplayName("Test getOverviewChart trả về đúng mảng nhãn và dữ liệu thu chi")
    void testGetOverviewChart() {
        when(txRepo.sumAmountByTypeAndDateRange(eq(1), any(), any(), any()))
                .thenReturn(BigDecimal.valueOf(200000));

        ChartOverviewResponse chart = financeService.getOverviewChart(1, 6);

        assertNotNull(chart);
        assertEquals(6, chart.getLabels().size());
        assertEquals(6, chart.getIncomeData().size());
        assertEquals(6, chart.getExpenseData().size());
    }

    @Test
    @DisplayName("Test getCategoryBreakdown tính % phân bổ chi phí theo danh mục")
    void testGetCategoryBreakdown() {
        Object[] row1 = new Object[]{TransactionCategory.EVENTS, BigDecimal.valueOf(600000)};
        Object[] row2 = new Object[]{TransactionCategory.MEDIA, BigDecimal.valueOf(400000)};

        when(txRepo.getExpenseBreakdownByCategory(1))
                .thenReturn(List.of(row1, row2));

        List<CategoryBreakdownResponse> breakdown = financeService.getCategoryBreakdown(1);

        assertNotNull(breakdown);
        assertEquals(2, breakdown.size());
        assertEquals(60.0, breakdown.get(0).getPercentage());
        assertEquals(40.0, breakdown.get(1).getPercentage());
    }

    @Test
    @DisplayName("Test createTransaction tạo mới thành công giao dịch thu chi")
    void testCreateTransaction() {
        TransactionRequest req = new TransactionRequest();
        req.setDescription("Thue san khau");
        req.setType(TransactionType.EXPENSE);
        req.setAmount(BigDecimal.valueOf(1500000));
        req.setCategory(TransactionCategory.EVENTS);
        req.setStatus(TransactionStatus.COMPLETED);

        when(clubRepo.findById(1)).thenReturn(Optional.of(sampleClub));
        when(userRepo.findById(10)).thenReturn(Optional.of(sampleUser));
        when(txRepo.save(any())).thenReturn(sampleTx);

        TransactionResponse res = financeService.createTransaction(1, 10, req);

        assertNotNull(res);
        assertEquals(100, res.getId());
        assertEquals(1, res.getClubId());
        assertEquals("Nguyen Van A", res.getCreatedByName());
    }

    @Test
    @DisplayName("Test getTransactions phân trang và tìm kiếm giao dịch")
    void testGetTransactions() {
        Page<FinancialTransaction> page = new PageImpl<>(List.of(sampleTx));
        when(txRepo.searchTransactions(eq(1), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Page<TransactionResponse> result = financeService.getTransactions(1, "Thu", null, null, null, PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Thu phi gia nhap", result.getContent().get(0).getDescription());
    }

    @Test
    @DisplayName("Test updateStatus cập nhật trạng thái giao dịch")
    void testUpdateStatus() {
        when(txRepo.findById(100)).thenReturn(Optional.of(sampleTx));
        when(txRepo.save(any())).thenReturn(sampleTx);

        TransactionResponse res = financeService.updateStatus(100, TransactionStatus.CANCELLED);

        assertNotNull(res);
        verify(txRepo, times(1)).save(sampleTx);
    }

    @Test
    @DisplayName("Test deleteTransaction xóa giao dịch thành công")
    void testDeleteTransaction() {
        doNothing().when(txRepo).deleteById(100);

        financeService.deleteTransaction(100);

        verify(txRepo, times(1)).deleteById(100);
    }

    @Test
    @DisplayName("Test exportTransactionsToExcel tạo file byte array Excel thành công")
    void testExportTransactionsToExcel() throws IOException {
        when(txRepo.findAllByClubIdOrderByCreatedAtDesc(1))
                .thenReturn(List.of(sampleTx));

        byte[] excelBytes = financeService.exportTransactionsToExcel(1);

        assertNotNull(excelBytes);
        assertTrue(excelBytes.length > 0);
    }
}
