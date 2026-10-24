package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.*;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.FinanceDtos.CreateCampaignRequest;
import com.example.clubmanagement.dto.FinanceDtos.FundCampaignResponse;
import com.example.clubmanagement.dto.FinanceDtos.FundContributionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FundCampaignService {

    private final FundCampaignRepository campaignRepo;
    private final FundContributionRepository contributionRepo;
    private final ClubMemberRepository clubMemberRepo;
    private final ClubRepository clubRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    @Transactional
    public FundCampaignResponse createCampaign(Integer clubId, Integer userId, CreateCampaignRequest req) {
        Club club = clubRepo.findById(clubId).orElseThrow(() -> new RuntimeException("Club not found"));
        User creator = userId != null ? userRepo.findById(userId).orElse(null) : null;

        FundCampaign campaign = FundCampaign.builder()
                .club(club)
                .title(req.getTitle())
                .amountPerMember(req.getAmountPerMember())
                .deadline(req.getDeadline())
                .bankAccountInfo(req.getBankAccountInfo())
                .description(req.getDescription())
                .createdBy(creator)
                .build();

        FundCampaign savedCampaign = campaignRepo.save(campaign);

        // Khởi tạo trạng thái thu tiền cho toàn bộ thành viên CLB
        List<ClubMember> members = clubMemberRepo.findByClubId(clubId);
        List<FundContribution> contributions = new ArrayList<>(members.size());
        for (ClubMember member : members) {
            contributions.add(FundContribution.builder()
                    .campaign(savedCampaign)
                    .user(member.getUser())
                    .status(ContributionStatus.UNPAID)
                    .build());
        }
        contributionRepo.saveAll(contributions);

        // Gửi email/thông báo thông tin đợt đóng quỹ mới
        for (ClubMember member : members) {
            if (member.getUser() != null && member.getUser().getEmail() != null) {
                try {
                    emailService.sendEmail(member.getUser().getEmail(),
                            "Thông báo đóng quỹ: " + savedCampaign.getTitle(),
                            "Khoản thu: " + savedCampaign.getAmountPerMember() + " VND. Hạn nộp: " + savedCampaign.getDeadline());
                } catch (Exception ignored) {}
            }
        }

        return getCampaignById(savedCampaign.getId());
    }

    @Transactional(readOnly = true)
    public List<FundCampaignResponse> getCampaignsByClub(Integer clubId) {
        List<FundCampaign> campaigns = campaignRepo.getByClubIdOrderByCreatedAtDesc(clubId);
        if (campaigns.isEmpty()) {
            return List.of();
        }
        Map<Integer, long[]> stats = loadStats(campaigns.stream().map(FundCampaign::getId).toList());
        return campaigns.stream()
                .map(c -> mapCampaignToDto(c, stats.getOrDefault(c.getId(), new long[2])))
                .toList();
    }

    @Transactional(readOnly = true)
    public FundCampaignResponse getCampaignById(Integer campaignId) {
        FundCampaign campaign = campaignRepo.findById(campaignId)
                .orElseThrow(() -> new RuntimeException("Campaign not found with ID: " + campaignId));
        return mapCampaignToDto(campaign, loadStats(List.of(campaignId)).getOrDefault(campaignId, new long[2]));
    }

    @Transactional(readOnly = true)
    public List<FundContributionResponse> getContributionsByCampaign(Integer campaignId) {
        return contributionRepo.findByCampaignIdWithUser(campaignId)
                .stream()
                .map(this::mapContributionToDto)
                .toList();
    }

    @Transactional
    public void quickTickPayment(Integer contributionId, boolean isPaid) {
        FundContribution c = contributionRepo.findById(contributionId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));
        c.setStatus(isPaid ? ContributionStatus.PAID : ContributionStatus.UNPAID);
        c.setPaidAt(isPaid ? LocalDateTime.now() : null);
        contributionRepo.save(c);
    }

    @Transactional
    public void submitProof(Integer campaignId, Integer userId, String proofUrl) {
        FundContribution c = contributionRepo.findByCampaignIdAndUserUserId(campaignId, userId)
                .orElseThrow(() -> new RuntimeException("Contribution not found"));
        c.setProofImageUrl(proofUrl);
        c.setStatus(ContributionStatus.PENDING_APPROVAL);
        contributionRepo.save(c);
    }

    @Transactional(readOnly = true)
    public void remindUnpaidMembers(Integer campaignId) {
        List<FundContribution> unpaids = contributionRepo.findByCampaignIdAndStatusWithUser(campaignId, ContributionStatus.UNPAID);
        for (FundContribution c : unpaids) {
            if (c.getUser() != null && c.getUser().getEmail() != null) {
                try {
                    emailService.sendEmail(c.getUser().getEmail(),
                            "[Nhắc nhở] Đóng quỹ CLB: " + c.getCampaign().getTitle(),
                            "Bạn chưa đóng tiền quỹ. Vui lòng thanh toán trước hạn chót: " + c.getCampaign().getDeadline());
                } catch (Exception ignored) {}
            }
        }
    }

    /** [totalMembers, paidMembers] cho nhiều chiến dịch chỉ trong 1 câu query. */
    private Map<Integer, long[]> loadStats(Collection<Integer> campaignIds) {
        Map<Integer, long[]> stats = new HashMap<>();
        for (Object[] row : contributionRepo.aggregateStatsByCampaignIds(campaignIds, ContributionStatus.PAID)) {
            stats.put((Integer) row[0], new long[]{
                    ((Number) row[1]).longValue(),
                    row[2] == null ? 0L : ((Number) row[2]).longValue()
            });
        }
        return stats;
    }

    private FundCampaignResponse mapCampaignToDto(FundCampaign campaign, long[] stats) {
        int totalMembers = (int) stats[0];
        int paidMembers = (int) stats[1];
        BigDecimal amountPerMember = campaign.getAmountPerMember() != null ? campaign.getAmountPerMember() : BigDecimal.ZERO;
        BigDecimal totalCollected = amountPerMember.multiply(BigDecimal.valueOf(paidMembers));
        BigDecimal targetAmount = amountPerMember.multiply(BigDecimal.valueOf(totalMembers));

        return FundCampaignResponse.builder()
                .id(campaign.getId())
                .clubId(campaign.getClub() != null ? campaign.getClub().getId() : null)
                .title(campaign.getTitle())
                .amountPerMember(campaign.getAmountPerMember())
                .deadline(campaign.getDeadline())
                .bankAccountInfo(campaign.getBankAccountInfo())
                .description(campaign.getDescription())
                .createdByName(campaign.getCreatedBy() != null ? campaign.getCreatedBy().getFullName() : "N/A")
                .createdAt(campaign.getCreatedAt())
                .totalMembers(totalMembers)
                .paidMembers(paidMembers)
                .totalCollected(totalCollected)
                .targetAmount(targetAmount)
                .build();
    }

    private FundContributionResponse mapContributionToDto(FundContribution c) {
        User u = c.getUser();
        return FundContributionResponse.builder()
                .id(c.getId())
                .campaignId(c.getCampaign() != null ? c.getCampaign().getId() : null)
                .userId(u != null ? u.getUserId() : null)
                .fullName(u != null ? u.getFullName() : "N/A")
                .email(u != null ? u.getEmail() : "N/A")
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .status(c.getStatus())
                .proofImageUrl(c.getProofImageUrl())
                .paidAt(c.getPaidAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
