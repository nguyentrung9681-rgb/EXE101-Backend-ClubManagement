package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.ClubTask;
import com.example.clubmanagement.Entity.FinancialTransaction;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.TransactionType;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubTaskRepository;
import com.example.clubmanagement.Repository.FinancialTransactionRepository;
import com.example.clubmanagement.dto.ClubOverviewDashboardResponse;
import com.example.clubmanagement.dto.ClubOverviewDashboardResponse.MonthlyFund;
import com.example.clubmanagement.dto.ClubOverviewDashboardResponse.TaskProgress;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ClubDashboardService {

    private final ClubMemberRepository clubMemberRepository;
    private final ClubTaskRepository taskRepository;
    private final FinancialTransactionRepository transactionRepository;

    @Transactional(readOnly = true)
    public ClubOverviewDashboardResponse getOverview(Integer clubId, Integer userId, Integer year) {
        clubMemberRepository.findByClubIdAndUserUserIdAndStatus(clubId, userId, ClubMemberStatus.ACTIVE)
                .orElseThrow(() -> new SecurityException("Access denied: You are not a member of this club"));

        int fundYear = (year != null) ? year : LocalDate.now().getYear();

        // 1. Tổng thành viên
        long totalMembers = clubMemberRepository.countByClubIdAndStatus(clubId, ClubMemberStatus.ACTIVE);

        // 2 + 3. Task
        List<ClubTask> tasks = taskRepository.findByClubId(clubId);
        long done = 0, doing = 0, todo = 0;
        for (ClubTask t : tasks) {
            String s = t.getStatus();
            if ("DONE".equalsIgnoreCase(s)) done++;
            else if ("DOING".equalsIgnoreCase(s)) doing++;
            else todo++; // TODO hoặc null
        }
        long total = done + doing + todo;

        TaskProgress progress = TaskProgress.builder()
                .totalTasks(total)
                .completedTasks(done)
                .inProgressTasks(doing)
                .todoTasks(todo)
                .completedPercent(percent(done, total))
                .inProgressPercent(percent(doing, total))
                .todoPercent(percent(todo, total))
                .build();

        // 4. Thu / chi theo tháng
        BigDecimal[] income = new BigDecimal[12];
        BigDecimal[] expense = new BigDecimal[12];
        for (int i = 0; i < 12; i++) {
            income[i] = BigDecimal.ZERO;
            expense[i] = BigDecimal.ZERO;
        }
        LocalDateTime from = LocalDateTime.of(fundYear, 1, 1, 0, 0);
        List<FinancialTransaction> txs = transactionRepository.findCompletedBetween(clubId, from, from.plusYears(1));
        for (FinancialTransaction tx : txs) {
            if (tx.getCreatedAt() == null || tx.getAmount() == null) continue;
            int idx = tx.getCreatedAt().getMonthValue() - 1;
            if (tx.getType() == TransactionType.INCOME) income[idx] = income[idx].add(tx.getAmount());
            else if (tx.getType() == TransactionType.EXPENSE) expense[idx] = expense[idx].add(tx.getAmount());
        }
        List<MonthlyFund> monthly = new ArrayList<>(12);
        for (int i = 0; i < 12; i++) {
            monthly.add(MonthlyFund.builder()
                    .month(i + 1)
                    .totalIncome(income[i])
                    .totalExpense(expense[i])
                    .net(income[i].subtract(expense[i]))
                    .build());
        }

        return ClubOverviewDashboardResponse.builder()
                .clubId(clubId)
                .totalMembers(totalMembers)
                .unfinishedTasks(doing + todo)
                .taskProgress(progress)
                .fundYear(fundYear)
                .monthlyFund(monthly)
                .build();
    }

    private static double percent(long part, long total) {
        return total == 0 ? 0.0 : Math.round(part * 1000.0 / total) / 10.0;
    }
}
