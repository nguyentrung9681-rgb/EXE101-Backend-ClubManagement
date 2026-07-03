package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.ChatMessage;
import com.example.clubmanagement.Entity.Task;
import com.example.clubmanagement.Enum.TaskStatus;
import com.example.clubmanagement.Service.ChatMessageService;
import com.example.clubmanagement.Service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/departments/{departmentId}")
public class WorkspaceController {

    @Autowired
    private TaskService taskService;
    @Autowired
    private ChatMessageService chatMessageService;

    // --- KANBAN TASK API ---

    @GetMapping("/tasks")
    public ResponseEntity<?> getKanban(@RequestHeader("X-User-Id") Integer userId, @PathVariable Long departmentId) {
        try {
            return ResponseEntity.ok(taskService.getKanbanBoard(userId, departmentId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/tasks")
    public ResponseEntity<?> createTask(
            @RequestHeader("X-User-Id") Integer creatorId,
            @PathVariable Long departmentId,
            @RequestBody Map<String, Object> body) {
        try {
            String title = body.get("title").toString();
            String description = body.get("description") != null ? body.get("description").toString() : "";
            Integer assignedUserId = body.get("assignedUserId") != null ? Integer.valueOf(body.get("assignedUserId").toString()) : null;

            Task task = taskService.createTask(creatorId, departmentId, title, description, assignedUserId);
            return ResponseEntity.ok(task);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/tasks/{taskId}/status")
    public ResponseEntity<?> updateTaskStatus(
            @RequestHeader("X-User-Id") Integer userId,
            @PathVariable Long departmentId, // Nhận để đồng bộ cấu trúc URL
            @PathVariable Long taskId,
            @RequestParam TaskStatus status) {
        try {
            Task updatedTask = taskService.updateTaskStatus(userId, taskId, status);
            return ResponseEntity.ok(updatedTask);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // --- INTERNAL CHAT API (REST-based) ---

    @GetMapping("/messages")
    public ResponseEntity<?> getChatHistory(@RequestHeader("X-User-Id") Integer userId, @PathVariable Long departmentId) {
        try {
            return ResponseEntity.ok(chatMessageService.getChatHistory(userId, departmentId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(
            @RequestHeader("X-User-Id") Integer senderId,
            @PathVariable Long departmentId,
            @RequestBody Map<String, String> body) {
        try {
            ChatMessage msg = chatMessageService.sendMessage(senderId, departmentId, body.get("messageContent"));
            return ResponseEntity.ok(msg);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}