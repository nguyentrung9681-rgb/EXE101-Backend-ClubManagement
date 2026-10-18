package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Config.SecurityUtils;
import com.example.clubmanagement.Entity.ClubTask;
import com.example.clubmanagement.Entity.ClubTrelloConfig;
import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.ClubTrelloConfigRepository;
import com.example.clubmanagement.Service.ClubTaskService;
import com.example.clubmanagement.Service.TrelloService;
import com.example.clubmanagement.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.HtmlUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class ClubTaskController {

    @Autowired private TrelloService trelloService;
    @Autowired private ClubTaskService taskService;
    @Autowired private ClubTrelloConfigRepository trelloConfigRepository;
    @Autowired private ClubRepository clubRepository;
    @Autowired(required = false) private SimpMessagingTemplate messagingTemplate;

    @Value("${trello.redirect-uri:${app.frontend.trello-url:https://exe-ebon.vercel.app/api/trello/callback}}")
    private String frontendTrelloUrl;

    @GetMapping("/trello/connect")
    public ResponseEntity<?> connectTrello(@RequestParam Integer clubId) {
        try {
            String authUrl = trelloService.getAuthorizeUrl(clubId);
            return ResponseEntity.ok(Map.of("url", authUrl));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lấy trạng thái kết nối Trello của CLB (phục vụ Frontend kiểm tra và ẩn/hiện banner cảnh báo).
     * GET /api/trello/status?clubId=...
     */
    @GetMapping("/trello/status")
    public ResponseEntity<?> getTrelloStatus(@RequestParam Integer clubId) {
        try {
            Optional<ClubTrelloConfig> configOpt = trelloConfigRepository.findByClubId(clubId);
            if (configOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "connected", false,
                        "boardLinked", false,
                        "status", "NOT_CONNECTED",
                        "clubId", clubId
                ));
            }
            ClubTrelloConfig config = configOpt.get();
            boolean hasToken = config.getTrelloToken() != null && !config.getTrelloToken().trim().isEmpty();
            boolean boardLinked = config.getTrelloBoardId() != null && !config.getTrelloBoardId().trim().isEmpty();
            String status = (!hasToken) ? "NOT_CONNECTED" : (boardLinked ? "CONNECTED" : "TOKEN_SAVED");

            Map<String, Object> response = new HashMap<>();
            response.put("connected", hasToken);
            response.put("boardLinked", boardLinked);
            response.put("status", status);
            response.put("clubId", clubId);
            response.put("boardId", config.getTrelloBoardId());
            response.put("todoListId", config.getTodoListId());
            response.put("doingListId", config.getDoingListId());
            response.put("doneListId", config.getDoneListId());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/trello/callback")
    @ResponseBody
    public String trelloCallback(@RequestParam Integer clubId) {
        Integer safeClubId = clubId;
        String safeFrontendUrl = HtmlUtils.htmlEscape(frontendTrelloUrl);
        // Trả trang HTML an toàn chống Reflected XSS và gửi postMessage thông báo tab cha
        return "<html><body style='font-family:sans-serif; text-align:center; padding-top:50px;'>" +
                "<h2>Kết nối Trello thành công!</h2>" +
                "<p>Hệ thống đang chuyển hướng về ứng dụng...</p>" +
                "<script>" +
                "var hash = window.location.hash;" +
                "if(hash && hash.includes('token=')) {" +
                "  var token = encodeURIComponent(hash.split('token=')[1].split('&')[0]);" +
                "  fetch('/api/trello/save-token?clubId=" + safeClubId + "&token=' + token, {method:'POST'})" +
                "  .then(function() {" +
                "    try {" +
                "      if (window.opener) {" +
                "        window.opener.postMessage({ type: 'TRELLO_CONNECTED', clubId: " + safeClubId + " }, '*');" +
                "        setTimeout(function() { window.close(); }, 1000);" +
                "        return;" +
                "      }" +
                "    } catch(e) {}" +
                "    setTimeout(function() { window.location.href = '" + safeFrontendUrl + "?clubId=" + safeClubId + "'; }, 1500);" +
                "  });" +
                "} else {" +
                "  document.body.innerHTML = '<h1 style=\"color:red;\">Không tìm thấy Trello Token trên URL!</h1>';" +
                "}" +
                "</script></body></html>";
    }

    @PostMapping({"/trello/save-token", "/trello/save_token"})
    public ResponseEntity<?> saveToken(@RequestParam Integer clubId, @RequestParam String token) {
        try {
            Club club = clubRepository.findById(clubId).orElseThrow(() -> new RuntimeException("Club not found"));
            ClubTrelloConfig config = trelloConfigRepository.findByClubId(clubId)
                    .orElse(ClubTrelloConfig.builder().club(club).build());
            config.setTrelloToken(token);
            trelloConfigRepository.save(config);

            // Bắn realtime qua WebSocket để Frontend cập nhật ngay lập tức mà không cần Refresh
            if (messagingTemplate != null) {
                Map<String, Object> wsPayload = Map.of(
                        "event", "TRELLO_CONNECTED",
                        "clubId", clubId,
                        "status", "TOKEN_SAVED",
                        "connected", true,
                        "boardLinked", config.getTrelloBoardId() != null
                );
                messagingTemplate.convertAndSend("/topic/clubs/" + clubId + "/trello", (Object) wsPayload);
                messagingTemplate.convertAndSend("/topic/clubs/" + clubId, (Object) wsPayload);
            }

            return ResponseEntity.ok(Map.of(
                    "clubId", clubId,
                    "status", "CONNECTED",
                    "connected", true,
                    "boardLinked", config.getTrelloBoardId() != null,
                    "message", "Lưu Trello Token thành công"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/trello/boards")
    public ResponseEntity<?> getBoards(@RequestParam Integer clubId) {
        try {
            ClubTrelloConfig config = trelloConfigRepository.findByClubId(clubId)
                    .orElseThrow(() -> new RuntimeException("Trello not connected"));
            return ResponseEntity.ok(trelloService.getBoards(config.getTrelloToken()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/trello/link-board")
    public ResponseEntity<?> linkBoard(@RequestParam Integer clubId, @RequestParam String boardId) {
        try {
            ClubTrelloConfig config = trelloConfigRepository.findByClubId(clubId)
                    .orElseThrow(() -> new RuntimeException("Trello config not found"));

            config.setTrelloBoardId(boardId);

            Map<String, String> listIds = trelloService.createDefaultLists(config.getTrelloToken(), boardId);
            config.setTodoListId(listIds.get("todoListId"));
            config.setDoingListId(listIds.get("doingListId"));
            config.setDoneListId(listIds.get("doneListId"));

            String webhookId = trelloService.registerWebhook(config.getTrelloToken(), boardId, clubId);
            config.setWebhookId(webhookId);

            trelloConfigRepository.save(config);

            // Bắn realtime qua WebSocket để Frontend nhận biết Board đã được liên kết
            if (messagingTemplate != null) {
                Map<String, Object> wsPayload = Map.of(
                        "event", "TRELLO_BOARD_LINKED",
                        "clubId", clubId,
                        "status", "CONNECTED",
                        "connected", true,
                        "boardLinked", true,
                        "boardId", boardId
                );
                messagingTemplate.convertAndSend("/topic/clubs/" + clubId + "/trello", (Object) wsPayload);
                messagingTemplate.convertAndSend("/topic/clubs/" + clubId, (Object) wsPayload);
            }

            return ResponseEntity.ok(Map.of("message", "Liên kết board Trello thành công!"));
        } catch (Exception e) {
            String errorMessage = (e.getMessage() != null && !e.getMessage().trim().isEmpty())
                    ? e.getMessage()
                    : e.getClass().getSimpleName();
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(
            @RequestBody ClubTaskRequest taskRequest,
            @RequestParam Integer clubId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(taskService.createTask(taskRequest, clubId, effectiveUserId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTasks(
            @RequestParam Integer clubId,
            @RequestParam(required = false) Integer userId,
            @RequestParam(required = false) String filterType,
            @RequestParam(required = false) Integer eventId,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(taskService.getTasks(clubId, effectiveUserId, filterType, eventId, department, status));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<?> updateTask(
            @PathVariable Integer taskId,
            @RequestBody ClubTaskRequest taskRequest,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(taskService.updateTask(taskId, taskRequest, effectiveUserId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<?> deleteTask(
            @PathVariable Integer taskId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            taskService.deleteTask(taskId, effectiveUserId);
            return ResponseEntity.ok(Map.of("message", "Xóa task thành công!"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/dashboard")
    public ResponseEntity<?> getDashboard(
            @RequestParam Integer clubId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(taskService.getDashboardStats(clubId, effectiveUserId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/reminders")
    public ResponseEntity<?> getReminders(
            @RequestParam Integer clubId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(taskService.getOverdueAndUpcomingReminders(clubId, effectiveUserId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/tasks/performance")
    public ResponseEntity<?> getPerformance(
            @RequestParam Integer clubId,
            @RequestParam(required = false) Integer userId) {
        try {
            Integer effectiveUserId = SecurityUtils.resolveUserId(userId);
            return ResponseEntity.ok(taskService.getMemberPerformanceReport(clubId, effectiveUserId));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
