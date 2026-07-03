package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubPost;
import com.example.clubmanagement.Service.ClubService;
import com.example.clubmanagement.dto.ClubRequest;
import com.example.clubmanagement.dto.ClubResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubService;

    public ClubController(ClubService clubService) {
        this.clubService = clubService;
    }

    //1.xem bài đăng public ở trang chủ hệ thống
    @GetMapping("/public-posts")
    public ResponseEntity<List<ClubPost>> getPublicPosts() {
        return ResponseEntity.ok(clubService.getPublicPosts());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Club>> searchClubs(@RequestParam String keyword) {
        return ResponseEntity.ok(clubService.searchClubs(keyword));
    }

    /**
     * Tạo một Câu lạc bộ mới.
     * POST /api/clubs?userId=1
     */
    @PostMapping
    public ResponseEntity<?> createClub(@RequestBody ClubRequest clubRequest, @RequestParam Integer userId) {
        try {
            Club club = Club.builder()
                    .name(clubRequest.getName())
                    .description(clubRequest.getDescription())
                    .logoUrl(clubRequest.getLogoUrl())
                    .build();
            Club created = clubService.createClub(club, userId);
            return ResponseEntity.ok(mapToClubResponse(created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách tất cả Câu lạc bộ.
     * GET /api/clubs
     */
    @GetMapping
    public ResponseEntity<List<ClubResponse>> getAllClubs() {
        List<ClubResponse> responses = clubService.getAllClubs().stream()
                .map(this::mapToClubResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Lấy thông tin Câu lạc bộ theo ID.
     * GET /api/clubs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getClubById(@PathVariable Integer id) {
        try {
            Club club = clubService.getClubById(id);
            return ResponseEntity.ok(mapToClubResponse(club));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private ClubResponse mapToClubResponse(Club club) {
        if (club == null) return null;
        return ClubResponse.builder()
                .id(club.getId())
                .name(club.getName())
                .description(club.getDescription())
                .logoUrl(club.getLogoUrl())
                .status(club.getStatus() != null ? club.getStatus().name() : null)
                .createdByUserId(club.getCreatedBy() != null ? club.getCreatedBy().getUserId() : null)
                .createdByName(club.getCreatedBy() != null ? club.getCreatedBy().getFullName() : null)
                .createdAt(club.getCreatedAt())
                .updatedAt(club.getUpdatedAt())
                .build();
    }
}
