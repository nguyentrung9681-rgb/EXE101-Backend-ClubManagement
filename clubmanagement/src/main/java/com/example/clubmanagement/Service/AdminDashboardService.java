package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.ClubStatus;
import com.example.clubmanagement.Enum.ClubVisibility;
import com.example.clubmanagement.Enum.SystemRole;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.AdminClubResponse;
import com.example.clubmanagement.dto.AdminDashboardOverviewResponse;
import com.example.clubmanagement.dto.AdminUserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final ClubDocumentRepository clubDocumentRepository;
    private final ClubEventRepository clubEventRepository;
    private final GoogleFormRepository googleFormRepository;
    private final GoogleSheetRepository googleSheetRepository;
    private final ClubTrelloConfigRepository clubTrelloConfigRepository;

    public AdminDashboardService(
            UserRepository userRepository,
            ClubRepository clubRepository,
            ClubMemberRepository clubMemberRepository,
            ClubDocumentRepository clubDocumentRepository,
            ClubEventRepository clubEventRepository,
            GoogleFormRepository googleFormRepository,
            GoogleSheetRepository googleSheetRepository,
            ClubTrelloConfigRepository clubTrelloConfigRepository) {
        this.userRepository = userRepository;
        this.clubRepository = clubRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.clubDocumentRepository = clubDocumentRepository;
        this.clubEventRepository = clubEventRepository;
        this.googleFormRepository = googleFormRepository;
        this.googleSheetRepository = googleSheetRepository;
        this.clubTrelloConfigRepository = clubTrelloConfigRepository;
    }

    /**
     * Lấy dữ liệu tổng quan cho trang Dashboard Admin
     */
    public AdminDashboardOverviewResponse getDashboardOverview() {
        // 1. Thống kê số lượng tổng quan
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByUserStatus("ACTIVE");
        long bannedUsers = userRepository.countByUserStatus("BANNED");

        long totalClubs = clubRepository.count();
        long activeClubs = clubRepository.countByStatus(ClubStatus.ACTIVE);
        long publicClubs = clubRepository.countByVisibility(ClubVisibility.PUBLIC);
        long privateClubs = clubRepository.countByVisibility(ClubVisibility.PRIVATE);

        long totalDocuments = clubDocumentRepository.count();
        long totalEvents = clubEventRepository.count();
        long totalForms = googleFormRepository.count();
        long totalSheets = googleSheetRepository.count();

        AdminDashboardOverviewResponse.DashboardStats stats = AdminDashboardOverviewResponse.DashboardStats.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .bannedUsers(bannedUsers)
                .totalClubs(totalClubs)
                .activeClubs(activeClubs)
                .publicClubs(publicClubs)
                .privateClubs(privateClubs)
                .totalDocuments(totalDocuments)
                .totalEvents(totalEvents)
                .totalForms(totalForms)
                .totalSheets(totalSheets)
                .build();

        // 2. Thống kê tài nguyên hàng tháng (7 tháng gần nhất)
        List<AdminDashboardOverviewResponse.MonthlyResourceStat> monthlyResourceUsage = getMonthlyResourceUsage(7);

        // 3. Danh sách câu lạc bộ hiện tại (Current Clubs)
        List<AdminClubResponse> currentClubs = getAllClubs(null, null, null).stream()
                .limit(10)
                .collect(Collectors.toList());

        // 4. Các hoạt động gần đây của toàn hệ thống (Recent System Activity)
        List<AdminDashboardOverviewResponse.RecentActivityResponse> recentActivities = getRecentActivities(15);

        return AdminDashboardOverviewResponse.builder()
                .stats(stats)
                .monthlyResourceUsage(monthlyResourceUsage)
                .currentClubs(currentClubs)
                .recentActivities(recentActivities)
                .build();
    }

    /**
     * Lấy danh sách toàn bộ người dùng sử dụng hệ thống, kèm bộ lọc tìm kiếm
     */
    public List<AdminUserResponse> getAllUsers(String search, String status, String authProvider) {
        List<User> users = userRepository.findAll();

        return users.stream()
                .filter(u -> {
                    if (search != null && !search.trim().isEmpty()) {
                        String s = search.trim().toLowerCase();
                        boolean matchName = u.getFullName() != null && u.getFullName().toLowerCase().contains(s);
                        boolean matchEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(s);
                        boolean matchUsername = u.getUsername() != null && u.getUsername().toLowerCase().contains(s);
                        if (!matchName && !matchEmail && !matchUsername) return false;
                    }
                    if (status != null && !status.trim().isEmpty()) {
                        if (u.getUserStatus() == null || !u.getUserStatus().equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    if (authProvider != null && !authProvider.trim().isEmpty()) {
                        if (u.getAuthProvider() == null || !u.getAuthProvider().equalsIgnoreCase(authProvider.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(User::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToAdminUserResponse)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách toàn bộ Câu lạc bộ sử dụng trong hệ thống, kèm bộ lọc tìm kiếm
     */
    public List<AdminClubResponse> getAllClubs(String search, String status, String visibility) {
        List<Club> clubs = clubRepository.findAll();

        return clubs.stream()
                .filter(c -> {
                    if (search != null && !search.trim().isEmpty()) {
                        String s = search.trim().toLowerCase();
                        boolean matchName = c.getName() != null && c.getName().toLowerCase().contains(s);
                        boolean matchDesc = c.getDescription() != null && c.getDescription().toLowerCase().contains(s);
                        boolean matchManager = c.getCreatedBy() != null && c.getCreatedBy().getFullName() != null
                                && c.getCreatedBy().getFullName().toLowerCase().contains(s);
                        if (!matchName && !matchDesc && !matchManager) return false;
                    }
                    if (status != null && !status.trim().isEmpty()) {
                        if (c.getStatus() == null || !c.getStatus().name().equalsIgnoreCase(status.trim())) {
                            return false;
                        }
                    }
                    if (visibility != null && !visibility.trim().isEmpty()) {
                        if (c.getVisibility() == null || !c.getVisibility().name().equalsIgnoreCase(visibility.trim())) {
                            return false;
                        }
                    }
                    return true;
                })
                .sorted(Comparator.comparing(Club::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::mapToAdminClubResponse)
                .collect(Collectors.toList());
    }

    private AdminUserResponse mapToAdminUserResponse(User user) {
        List<ClubMember> memberships = clubMemberRepository.findByUserUserId(user.getUserId());

        int joinedClubsCount = (int) memberships.stream()
                .filter(m -> m.getStatus() == ClubMemberStatus.ACTIVE)
                .count();

        int managedClubsCount = (int) memberships.stream()
                .filter(m -> m.getRole() == ClubMemberRole.PRESIDENT && m.getStatus() == ClubMemberStatus.ACTIVE)
                .count();

        List<AdminUserResponse.UserClubMembershipInfo> clubInfos = memberships.stream()
                .map(m -> AdminUserResponse.UserClubMembershipInfo.builder()
                        .clubId(m.getClub() != null ? m.getClub().getId() : null)
                        .clubName(m.getClub() != null ? m.getClub().getName() : null)
                        .role(m.getRole() != null ? m.getRole().name() : null)
                        .status(m.getStatus() != null ? m.getStatus().name() : null)
                        .departmentName(m.getDepartment() != null ? m.getDepartment().getName() : null)
                        .build())
                .collect(Collectors.toList());

        return AdminUserResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .authProvider(user.getAuthProvider())
                .userStatus(user.getUserStatus())
                .role(user.getRole() != null ? user.getRole().name() : SystemRole.USER.name())
                .createdAt(user.getCreatedAt())
                .joinedClubsCount(joinedClubsCount)
                .managedClubsCount(managedClubsCount)
                .clubs(clubInfos)
                .build();
    }

    /**
     * Cập nhật vai trò hệ thống của người dùng (USER / ADMIN)
     */
    @Transactional
    public void updateUserRole(Integer userId, SystemRole newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        user.setRole(newRole);
        userRepository.save(user);
    }

    private AdminClubResponse mapToAdminClubResponse(Club club) {
        long memberCount = clubMemberRepository.countByClubId(club.getId());
        long documentCount = clubDocumentRepository.countByClubId(club.getId());
        long eventCount = clubEventRepository.countByClubId(club.getId());

        User manager = club.getCreatedBy();

        return AdminClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(club.getLogoUrl())
                .visibility(club.getVisibility() != null ? club.getVisibility().name() : null)
                .status(club.getStatus() != null ? club.getStatus().name() : null)
                .managerId(manager != null ? manager.getUserId() : null)
                .managerName(manager != null ? manager.getFullName() : null)
                .managerEmail(manager != null ? manager.getEmail() : null)
                .memberCount(memberCount)
                .documentCount(documentCount)
                .eventCount(eventCount)
                .createdAt(club.getCreatedAt())
                .updatedAt(club.getUpdatedAt())
                .build();
    }

    private List<AdminDashboardOverviewResponse.MonthlyResourceStat> getMonthlyResourceUsage(int monthsCount) {
        LocalDateTime now = LocalDateTime.now();
        List<AdminDashboardOverviewResponse.MonthlyResourceStat> result = new ArrayList<>();

        List<ClubDocument> allDocs = clubDocumentRepository.findAll();
        List<ClubEvent> allEvents = clubEventRepository.findAll();
        List<GoogleForm> allForms = googleFormRepository.findAll();
        List<GoogleSheet> allSheets = googleSheetRepository.findAll();

        for (int i = monthsCount - 1; i >= 0; i--) {
            LocalDateTime targetMonth = now.minusMonths(i);
            int year = targetMonth.getYear();
            int monthValue = targetMonth.getMonthValue();
            String monthName = targetMonth.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);

            long docCount = allDocs.stream()
                    .filter(d -> d.getCreatedAt() != null && d.getCreatedAt().getYear() == year && d.getCreatedAt().getMonthValue() == monthValue)
                    .count();

            long eventCount = allEvents.stream()
                    .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().getYear() == year && e.getCreatedAt().getMonthValue() == monthValue)
                    .count();

            long formCount = allForms.stream()
                    .filter(f -> f.getCreatedAt() != null && f.getCreatedAt().getYear() == year && f.getCreatedAt().getMonthValue() == monthValue)
                    .count();

            long sheetCount = allSheets.stream()
                    .filter(s -> s.getCreatedAt() != null && s.getCreatedAt().getYear() == year && s.getCreatedAt().getMonthValue() == monthValue)
                    .count();

            result.add(AdminDashboardOverviewResponse.MonthlyResourceStat.builder()
                    .month(monthName)
                    .monthValue(monthValue)
                    .year(year)
                    .documents(docCount)
                    .events(eventCount)
                    .forms(formCount)
                    .sheets(sheetCount)
                    .build());
        }

        return result;
    }

    private List<AdminDashboardOverviewResponse.RecentActivityResponse> getRecentActivities(int limit) {
        List<AdminDashboardOverviewResponse.RecentActivityResponse> activities = new ArrayList<>();

        // 1. CLB mới tạo
        clubRepository.findAll().stream()
                .filter(c -> c.getCreatedAt() != null)
                .sorted(Comparator.comparing(Club::getCreatedAt).reversed())
                .limit(5)
                .forEach(c -> activities.add(AdminDashboardOverviewResponse.RecentActivityResponse.builder()
                        .id("club-" + c.getId())
                        .actorName(c.getCreatedBy() != null ? c.getCreatedBy().getFullName() : "Hệ thống")
                        .actorAvatar(c.getCreatedBy() != null ? c.getCreatedBy().getAvatarUrl() : null)
                        .action("created a new club")
                        .targetName(c.getName())
                        .type("CLUB")
                        .timestamp(c.getCreatedAt())
                        .timeAgo(formatTimeAgo(c.getCreatedAt()))
                        .build()));

        // 2. Tài liệu mới tạo
        clubDocumentRepository.findAll().stream()
                .filter(d -> d.getCreatedAt() != null)
                .sorted(Comparator.comparing(ClubDocument::getCreatedAt).reversed())
                .limit(5)
                .forEach(d -> activities.add(AdminDashboardOverviewResponse.RecentActivityResponse.builder()
                        .id("doc-" + d.getId())
                        .actorName(d.getCreatedBy() != null ? d.getCreatedBy().getFullName() : "Thành viên CLB")
                        .actorAvatar(d.getCreatedBy() != null ? d.getCreatedBy().getAvatarUrl() : null)
                        .action("created a Google Document")
                        .targetName(d.getTitle())
                        .type("DOCUMENT")
                        .timestamp(d.getCreatedAt())
                        .timeAgo(formatTimeAgo(d.getCreatedAt()))
                        .build()));

        // 3. Sự kiện mới tạo
        clubEventRepository.findAll().stream()
                .filter(e -> e.getCreatedAt() != null)
                .sorted(Comparator.comparing(ClubEvent::getCreatedAt).reversed())
                .limit(5)
                .forEach(e -> activities.add(AdminDashboardOverviewResponse.RecentActivityResponse.builder()
                        .id("event-" + e.getId())
                        .actorName(e.getCreatedBy() != null ? e.getCreatedBy().getFullName() : "Thành viên CLB")
                        .actorAvatar(e.getCreatedBy() != null ? e.getCreatedBy().getAvatarUrl() : null)
                        .action("created an event")
                        .targetName(e.getTitle())
                        .type("EVENT")
                        .timestamp(e.getCreatedAt())
                        .timeAgo(formatTimeAgo(e.getCreatedAt()))
                        .build()));

        // 4. Tài khoản mới đăng ký
        userRepository.findAll().stream()
                .filter(u -> u.getCreatedAt() != null)
                .sorted(Comparator.comparing(User::getCreatedAt).reversed())
                .limit(5)
                .forEach(u -> activities.add(AdminDashboardOverviewResponse.RecentActivityResponse.builder()
                        .id("user-" + u.getUserId())
                        .actorName(u.getFullName() != null ? u.getFullName() : u.getUsername())
                        .actorAvatar(u.getAvatarUrl())
                        .action("registered an account")
                        .targetName("New User")
                        .type("USER")
                        .timestamp(u.getCreatedAt())
                        .timeAgo(formatTimeAgo(u.getCreatedAt()))
                        .build()));

        // 5. Kết nối Trello
        clubTrelloConfigRepository.findAll().stream()
                .filter(t -> t.getCreatedAt() != null)
                .sorted(Comparator.comparing(ClubTrelloConfig::getCreatedAt).reversed())
                .limit(5)
                .forEach(t -> {
                    String clubName = t.getClub() != null ? t.getClub().getName() : "CLB";
                    String actorName = (t.getClub() != null && t.getClub().getCreatedBy() != null)
                            ? t.getClub().getCreatedBy().getFullName()
                            : "Chủ nhiệm CLB";
                    activities.add(AdminDashboardOverviewResponse.RecentActivityResponse.builder()
                            .id("trello-" + t.getId())
                            .actorName(actorName)
                            .action("connected Trello")
                            .targetName(clubName + " Board")
                            .type("TRELLO")
                            .timestamp(t.getCreatedAt())
                            .timeAgo(formatTimeAgo(t.getCreatedAt()))
                            .build());
                });

        return activities.stream()
                .sorted(Comparator.comparing(AdminDashboardOverviewResponse.RecentActivityResponse::getTimestamp, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(limit)
                .collect(Collectors.toList());
    }

    private String formatTimeAgo(LocalDateTime dateTime) {
        if (dateTime == null) return "gần đây";
        Duration duration = Duration.between(dateTime, LocalDateTime.now());
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return "vài giây trước";
        }
        long minutes = duration.toMinutes();
        if (minutes < 60) {
            return minutes + " phút trước";
        }
        long hours = duration.toHours();
        if (hours < 24) {
            return hours + " giờ trước";
        }
        long days = duration.toDays();
        if (days < 30) {
            return days + " ngày trước";
        }
        long months = days / 30;
        if (months < 12) {
            return months + " tháng trước";
        }
        long years = days / 365;
        return years + " năm trước";
    }
}
