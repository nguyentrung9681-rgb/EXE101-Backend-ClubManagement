package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.RecruitmentApplication;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ApplicationStatus;
import com.example.clubmanagement.Enum.ClubRole;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.RecruitmentApplicationRepository;
import com.example.clubmanagement.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RecruitmentService {
    @Autowired
    private RecruitmentApplicationRepository applicationRepository;
    @Autowired
    private ClubMemberRepository clubMemberRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ClubRepository clubRepository;

    private static final String REJECT_MESSAGE_DEFAULT =
            "Xin chân thành cảm ơn bạn đã muốn tham gia clb nhưng hiện tại vì số yêu cầu bạn chưa đáp ứng, chúc bạn may mắn lần sau";

    public RecruitmentApplication sendJoinRequest(Integer userId, Integer clubId, String content) {
        // 1. Kiểm tra xem đã là thành viên chưa
        if (clubMemberRepository.existsByUserUserIdAndClubId(userId, clubId)) {
            throw new RuntimeException("Bạn đã là thành viên của câu lạc bộ này rồi.");
        }

        // 2. Kiểm tra xem có đơn nào đang chờ duyệt (PENDING) không
        if (applicationRepository.existsByUserUserIdAndClubIdAndStatus(userId, clubId, ApplicationStatus.PENDING)) {
            throw new RuntimeException("Yêu cầu tham gia của bạn đang được xét duyệt, không thể gửi thêm.");
        }

        User user = userRepository.findById(userId).orElseThrow(() -> new RuntimeException("Không tìm thấy User"));
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ"));

        RecruitmentApplication application = RecruitmentApplication.builder()
                .user(user)
                .club(club)
                .applicationContent(content)
                .status(ApplicationStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        return applicationRepository.save(application);
    }

    public List<RecruitmentApplication> getPendingRequests(Integer clubId) {
        return applicationRepository.findByClubIdAndStatus(clubId, ApplicationStatus.PENDING);
    }

    @Transactional
    public void approveRequest(Long requestId) {
        RecruitmentApplication app = applicationRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đăng ký"));

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new RuntimeException("Đơn này đã được xử lý từ trước.");
        }

        // Cập nhật trạng thái đơn thành APPROVED
        app.setStatus(ApplicationStatus.APPROVED);
        applicationRepository.save(app);

        // Thêm User này vào bảng thành viên câu lạc bộ với vai trò MEMBER mặc định
        ClubMember newMember = ClubMember.builder()
                .user(app.getUser())
                .club(app.getClub())
                .role(ClubRole.MEMBER)
                .build();

        clubMemberRepository.save(newMember);
    }

    public RecruitmentApplication rejectRequest(Long requestId) {
        RecruitmentApplication app = applicationRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đăng ký"));

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new RuntimeException("Đơn này đã được xử lý từ trước.");
        }

        // Cập nhật trạng thái đơn thành REJECTED và lưu câu thông báo mặc định
        app.setStatus(ApplicationStatus.REJECTED);
        app.setRejectReason(REJECT_MESSAGE_DEFAULT);

        return applicationRepository.save(app);
    }
}
