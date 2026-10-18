package com.example.clubmanagement.Controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@Tag(name = "Health Check", description = "Kiểm tra tình trạng server (Health check / Keep-alive)")
public class HealthCheckController {

    @Operation(summary = "Kiểm tra server còn hoạt động hay không")
    @GetMapping({"/health", "/api/health"})
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Server is running",
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
