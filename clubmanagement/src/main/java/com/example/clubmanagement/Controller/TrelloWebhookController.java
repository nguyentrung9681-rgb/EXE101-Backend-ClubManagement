package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Service.ClubTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/trello/webhook")
public class TrelloWebhookController {

    @Autowired
    private ClubTaskService taskService;

    // Trello gửi request HEAD để check webhook URL có hoạt động public không
    @RequestMapping(method = RequestMethod.HEAD)
    public ResponseEntity<Void> verifyWebhook() {
        return ResponseEntity.ok().build();
    }

    // Nhận dữ liệu cập nhật sự kiện từ Trello Board
    @PostMapping
    public ResponseEntity<Void> receiveWebhook(@RequestBody Map<String, Object> payload) {
        taskService.processWebhook(payload);
        return ResponseEntity.ok().build();
    }
}