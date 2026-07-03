package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubPost;
import com.example.clubmanagement.Service.ClubService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/clubs")
public class ClubController {
    @Autowired
    private ClubService clubService;

    //1.xem bài đăng public ở trang chủ hệ thống
    @GetMapping("/public-posts")
    public ResponseEntity<List<ClubPost>> getPublicPosts() {
        return ResponseEntity.ok(clubService.getPublicPosts());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Club>> searchClubs(@RequestParam String keyword) {
        return ResponseEntity.ok(clubService.searchClubs(keyword));
    }
}
