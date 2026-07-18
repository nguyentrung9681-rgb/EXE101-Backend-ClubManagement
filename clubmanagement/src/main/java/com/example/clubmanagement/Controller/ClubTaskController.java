package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.ClubTask;
import com.example.clubmanagement.Entity.ClubTrelloConfig;
import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.ClubTrelloConfigRepository;
import com.example.clubmanagement.Service.ClubTaskService;
import com.example.clubmanagement.Service.TrelloService;
import com.example.clubmanagement.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ClubTaskController {

    @Autowired private TrelloService trelloService;
    @Autowired private ClubTaskService taskService;
    @Autowired private ClubTrelloConfigRepository trelloConfigRepository;
    @Autowired private ClubRepository clubRepository;

    @GetMapping("/trello/connect")
    public ResponseEntity<Map<String, String>> connectTrello(@RequestParam Integer clubId) {
        String authUrl = trelloService.getAuthorizeUrl(clubId);
        return ResponseEntity.ok(Map.of("url", authUrl));
    }

    @GetMapping("/trello/callback")
    @ResponseBody
    public String trelloCallback(@RequestParam Integer clubId) {
        // Trả trang HTML để đọc token từ fragment #token và post ngược về /api/trello/save-token
        return "<html><body><script>" +
                "var hash = window.location.hash;" +
                "if(hash && hash.includes('token=')) {" +
                "  var token = hash.split('token=')[1].split('&')[0];" +
                "  fetch('/api/trello/save-token?clubId=" + clubId + "&token=' + token, {method:'POST'})" +
                "  .then(() => document.body.innerHTML = '<h1>Ket noi Trello thanh cong! Ban co the dong tab nay.</h1>');" +
                "}" +
                "</script></body></html>";
    }

    @PostMapping("/trello/save-token")
    public ResponseEntity<Void> saveToken(@RequestParam Integer clubId, @RequestParam String token) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new RuntimeException("Club not found"));
        ClubTrelloConfig config = trelloConfigRepository.findByClubId(clubId)
                .orElse(ClubTrelloConfig.builder().club(club).build());
        config.setTrelloToken(token);
        trelloConfigRepository.save(config);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/trello/boards")
    public ResponseEntity<List<Map<String, Object>>> getBoards(@RequestParam Integer clubId) {
        ClubTrelloConfig config = trelloConfigRepository.findByClubId(clubId)
                .orElseThrow(() -> new RuntimeException("Trello not connected"));
        return ResponseEntity.ok(trelloService.getBoards(config.getTrelloToken()));
    }

    @PostMapping("/trello/link-board")
    public ResponseEntity<Void> linkBoard(@RequestParam Integer clubId, @RequestParam String boardId) {
        ClubTrelloConfig config = trelloConfigRepository.findByClubId(clubId)
                .orElseThrow(() -> new RuntimeException("Trello config not found"));

        config.setTrelloBoardId(boardId);

        // Tạo các danh sách mặc định To Do, Doing, Done
        Map<String, String> listIds = trelloService.createDefaultLists(config.getTrelloToken(), boardId);
        config.setTodoListId(listIds.get("todoListId"));
        config.setDoingListId(listIds.get("doingListId"));
        config.setDoneListId(listIds.get("doneListId"));

        // Đăng ký webhook để đồng bộ ngược dữ liệu từ Trello về DB SClub
        String webhookId = trelloService.registerWebhook(config.getTrelloToken(), boardId, clubId);
        config.setWebhookId(webhookId);

        trelloConfigRepository.save(config);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/tasks")
    public ResponseEntity<ClubTaskResponse> createTask(
            @RequestBody ClubTaskRequest taskRequest,
            @RequestParam Integer clubId,
            @RequestParam Integer userId) {
        return ResponseEntity.ok(taskService.createTask(taskRequest, clubId, userId));
    }

    @GetMapping("/tasks")
    public ResponseEntity<List<ClubTaskResponse>> getTasks(
            @RequestParam Integer clubId,
            @RequestParam Integer userId,
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(taskService.getTasks(clubId, userId, filterType, eventId, department, status));
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<ClubTaskResponse> updateTask(
            @PathVariable Integer taskId,
            @RequestBody ClubTaskRequest taskRequest,
            @RequestParam Integer userId) {
        return ResponseEntity.ok(taskService.updateTask(taskId, taskRequest, userId));
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(
            @PathVariable Integer taskId,
            @RequestParam Integer userId) {
        taskService.deleteTask(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/tasks/dashboard")
    public ResponseEntity<ClubProgressDashboardResponse> getDashboard(
            @RequestParam Integer clubId,
            @RequestParam Integer userId) {
        return ResponseEntity.ok(taskService.getDashboardStats(clubId, userId));
    }

    @GetMapping("/tasks/reminders")
    public ResponseEntity<List<ClubTaskResponse>> getReminders(
            @RequestParam Integer clubId,
            @RequestParam Integer userId) {
        return ResponseEntity.ok(taskService.getOverdueAndUpcomingReminders(clubId, userId));
    }

    @GetMapping("/tasks/performance")
    public ResponseEntity<List<MemberPerformanceResponse>> getPerformance(
            @RequestParam Integer clubId,
            @RequestParam Integer userId) {
        return ResponseEntity.ok(taskService.getMemberPerformanceReport(clubId, userId));
    }
}
