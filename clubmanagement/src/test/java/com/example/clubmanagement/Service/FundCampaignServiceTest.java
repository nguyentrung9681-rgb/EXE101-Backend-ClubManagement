package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ContributionStatus;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.FinanceDtos.CreateCampaignRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FundCampaignServiceTest {

    @Mock
    private FundCampaignRepository campaignRepo;

    @Mock
    private FundContributionRepository contributionRepo;

    @Mock
    private ClubMemberRepository clubMemberRepo;

    @Mock
    private ClubRepository clubRepo;

    @Mock
    private UserRepository userRepo;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private FundCampaignService campaignService;

    private Club sampleClub;
    private User sampleUser;
    private FundCampaign sampleCampaign;
    private FundContribution sampleContribution;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        sampleClub = Club.builder().id(1).name("CLB Am Nhac").build();
        sampleUser = User.builder().userId(10).fullName("Tran Van B").email("tranvanb@example.com").build();

        sampleCampaign = FundCampaign.builder()
                .id(50)
                .club(sampleClub)
                .title("Quy CLB Thang 10")
                .amountPerMember(BigDecimal.valueOf(100000))
                .deadline(LocalDateTime.now().plusDays(7))
                .createdBy(sampleUser)
                .build();

        sampleContribution = FundContribution.builder()
                .id(200)
                .campaign(sampleCampaign)
                .user(sampleUser)
                .status(ContributionStatus.UNPAID)
                .build();
    }

    @Test
    @DisplayName("Test createCampaign tạo mới đợt thu quỹ và tự động khởi tạo trạng thái thu cho tất cả thành viên")
    void testCreateCampaign() {
        CreateCampaignRequest req = new CreateCampaignRequest();
        req.setTitle("Quy CLB Thang 10");
        req.setAmountPerMember(BigDecimal.valueOf(100000));
        req.setDeadline(LocalDateTime.now().plusDays(7));
        req.setBankAccountInfo("123456789 - MB Bank");
        req.setDescription("Thu phi sinh hoat thang 10");

        ClubMember member = ClubMember.builder().id(1).club(sampleClub).user(sampleUser).build();

        when(clubRepo.findById(1)).thenReturn(Optional.of(sampleClub));
        when(userRepo.findById(10)).thenReturn(Optional.of(sampleUser));
        when(campaignRepo.save(any())).thenReturn(sampleCampaign);
        when(clubMemberRepo.findByClubId(1)).thenReturn(List.of(member));

        FundCampaign result = campaignService.createCampaign(1, 10, req);

        assertNotNull(result);
        assertEquals(50, result.getId());
        verify(contributionRepo, times(1)).save(any());
        verify(emailService, times(1)).sendEmail(eq("tranvanb@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("Test quickTickPayment thủ quỹ tick nhanh đã đóng tiền")
    void testQuickTickPayment() {
        when(contributionRepo.findById(200)).thenReturn(Optional.of(sampleContribution));

        campaignService.quickTickPayment(200, true);

        assertEquals(ContributionStatus.PAID, sampleContribution.getStatus());
        assertNotNull(sampleContribution.getPaidAt());
        verify(contributionRepo, times(1)).save(sampleContribution);
    }

    @Test
    @DisplayName("Test submitProof thành viên upload bill chuyển khoản")
    void testSubmitProof() {
        when(contributionRepo.findByCampaignIdAndUserUserId(50, 10))
                .thenReturn(Optional.of(sampleContribution));

        campaignService.submitProof(50, 10, "https://storage.com/bill.png");

        assertEquals(ContributionStatus.PENDING_APPROVAL, sampleContribution.getStatus());
        assertEquals("https://storage.com/bill.png", sampleContribution.getProofImageUrl());
        verify(contributionRepo, times(1)).save(sampleContribution);
    }

    @Test
    @DisplayName("Test remindUnpaidMembers gửi email nhắc nhở hàng loạt thành viên chưa đóng tiền")
    void testRemindUnpaidMembers() {
        when(contributionRepo.findByCampaignIdAndStatus(50, ContributionStatus.UNPAID))
                .thenReturn(List.of(sampleContribution));

        campaignService.remindUnpaidMembers(50);

        verify(emailService, times(1)).sendEmail(eq("tranvanb@example.com"), contains("[Nhắc nhở]"), anyString());
    }
}
