package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.RecruitmentApplication;
import com.example.clubmanagement.Service.RecruitmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clubs")
public class RecruitmentController {
    @Autowired
    private RecruitmentService recruitmentService;

    //3.User gửi yêu cầu xin gia nhập clb/nhóm
    @PostMapping("/{clubId}/join-request")
    public ResponseEntity<?> joinRequest(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Long clubId,
            @RequestBody Map<String, String> body) {
        try {
            String content = body.get("applicationContent");
            RecruitmentApplication app = recruitmentService.sendJoinRequest(userId, clubId, content);
            return ResponseEntity.ok(app);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    //4.club manager lấy danh sách các đơn đang chờ duyệt của clb mình
    @GetMapping("/{clubId}/requests")
    public ResponseEntity<List<RecruitmentApplication>> getPendingRequests(@PathVariable Long clubId) {
        return ResponseEntity.ok(recruitmentService.getPendingRequests(clubId));
    }

    @PutMapping("/requests/{requestId}/approve")
    public ResponseEntity<String> approveRequest(@PathVariable long requestId) {
        try {
            recruitmentService.approveRequest(requestId);
            return ResponseEntity.ok("Đã duyệt thành viên vào câu lạc bộ thành công.");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/requests/{requestId}/reject")
    public ResponseEntity<?> rejectRequest(@PathVariable Long requestId) {
        try {
            RecruitmentApplication app = recruitmentService.rejectRequest(requestId);
            return ResponseEntity.ok(app);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
