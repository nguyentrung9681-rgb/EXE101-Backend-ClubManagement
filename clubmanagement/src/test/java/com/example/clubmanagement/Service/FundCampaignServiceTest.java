package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ContributionStatus;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.FinanceDtos.CreateCampaignRequest;
import com.example.clubmanagement.dto.FinanceDtos.FundCampaignResponse;
import com.example.clubmanagement.dto.FinanceDtos.FundContributionResponse;
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
        // createCampaign ket thuc goi getCampaignById -> can stub findById + query thong ke GROUP BY
        when(campaignRepo.findById(50)).thenReturn(Optional.of(sampleCampaign));
        when(contributionRepo.aggregateStatsByCampaignIds(anyCollection(), eq(ContributionStatus.PAID)))
                .thenReturn(List.<Object[]>of(new Object[]{50, 1L, 1L}));

        FundCampaignResponse result = campaignService.createCampaign(1, 10, req);

        assertNotNull(result);
        assertEquals(50, result.getId());
        verify(contributionRepo, times(1)).saveAll(any());
        verify(emailService, times(1)).sendEmail(eq("tranvanb@example.com"), anyString(), anyString());
    }

    @Test
    @DisplayName("Test getCampaignsByClub lấy danh sách đợt thu quỹ của CLB")
    void testGetCampaignsByClub() {
        when(campaignRepo.getByClubIdOrderByCreatedAtDesc(1))
                .thenReturn(List.of(sampleCampaign));
        when(contributionRepo.aggregateStatsByCampaignIds(anyCollection(), eq(ContributionStatus.PAID)))
                .thenReturn(List.<Object[]>of(new Object[]{50, 1L, 0L}));

        List<FundCampaignResponse> list = campaignService.getCampaignsByClub(1);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals("Quy CLB Thang 10", list.get(0).getTitle());
        assertEquals(1, list.get(0).getTotalMembers());
    }

    @Test
    @DisplayName("Test getCampaignById lấy chi tiết đợt thu quỹ")
    void testGetCampaignById() {
        when(campaignRepo.findById(50)).thenReturn(Optional.of(sampleCampaign));
        when(contributionRepo.findByCampaignId(50)).thenReturn(List.of(sampleContribution));

        FundCampaignResponse res = campaignService.getCampaignById(50);

        assertNotNull(res);
        assertEquals(50, res.getId());
        assertEquals("Quy CLB Thang 10", res.getTitle());
    }

    @Test
    @DisplayName("Test getContributionsByCampaign lấy danh sách đóng tiền của đợt thu quỹ")
    void testGetContributionsByCampaign() {
        when(contributionRepo.findByCampaignIdWithUser(50)).thenReturn(List.of(sampleContribution));

        List<FundContributionResponse> list = campaignService.getContributionsByCampaign(50);

        assertNotNull(list);
        assertEquals(1, list.size());
        assertEquals(200, list.get(0).getId());
        assertEquals("Tran Van B", list.get(0).getFullName());
        assertEquals(ContributionStatus.UNPAID, list.get(0).getStatus());
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
        when(contributionRepo.findByCampaignIdAndStatusWithUser(50, ContributionStatus.UNPAID))
                .thenReturn(List.of(sampleContribution));

        campaignService.remindUnpaidMembers(50);

        verify(emailService, times(1)).sendEmail(eq("tranvanb@example.com"), contains("[Nhắc nhở]"), anyString());
    }
}
