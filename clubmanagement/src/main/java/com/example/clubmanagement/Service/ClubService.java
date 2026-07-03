package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.ClubStatus;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ClubService {

    private final ClubRepository clubRepository;
    private final ClubMemberRepository clubMemberRepository;
    private final UserRepository userRepository;

    public ClubService(ClubRepository clubRepository, ClubMemberRepository clubMemberRepository, UserRepository userRepository) {
        this.clubRepository = clubRepository;
        this.clubMemberRepository = clubMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * Tạo một câu lạc bộ mới và tự động gán người tạo làm Chủ nhiệm (PRESIDENT).
     */
    @Transactional
    public Club createClub(Club club, Integer userId) {
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        club.setCreatedBy(creator);
        club.setStatus(ClubStatus.ACTIVE);
        Club savedClub = clubRepository.save(club);

        // Tự động thêm chủ nhiệm vào danh sách thành viên
        ClubMember president = ClubMember.builder()
                .club(savedClub)
                .user(creator)
                .role(ClubMemberRole.PRESIDENT)
                .status(ClubMemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        clubMemberRepository.save(president);

        return savedClub;
    }

    public List<Club> getAllClubs() {
        return clubRepository.findAll();
    }

    public Club getClubById(Integer id) {
        return clubRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
    }
}
