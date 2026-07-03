package com.example.clubmanagement.Controller;

import com.example.clubmanagement.Entity.ClubEvent;
import com.example.clubmanagement.Entity.EventGoogleSync;
import com.example.clubmanagement.Service.ClubEventService;
import com.example.clubmanagement.dto.ClubEventRequest;
import com.example.clubmanagement.dto.ClubEventResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
public class ClubEventController {

    private final ClubEventService clubEventService;

    public ClubEventController(ClubEventService clubEventService) {
        this.clubEventService = clubEventService;
    }

    /**
     * Tạo một sự kiện hoạt động CLB mới.
     * POST /api/events?clubId=1&userId=1
     */
    @PostMapping
    public ResponseEntity<?> createEvent(@RequestBody ClubEventRequest eventRequest,
                                         @RequestParam Integer clubId,
                                         @RequestParam Integer userId) {
        try {
            ClubEvent event = ClubEvent.builder()
                    .title(eventRequest.getTitle())
                    .description(eventRequest.getDescription())
                    .startTime(eventRequest.getStartTime())
                    .endTime(eventRequest.getEndTime())
                    .location(eventRequest.getLocation())
                    .build();
            ClubEvent created = clubEventService.createEvent(event, clubId, userId);
            return ResponseEntity.ok(mapToClubEventResponse(created));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Cập nhật thông tin sự kiện.
     * PUT /api/events/{id}?userId=1
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateEvent(@PathVariable Integer id,
                                         @RequestBody ClubEventRequest eventRequest,
                                         @RequestParam Integer userId) {
        try {
            ClubEvent eventDetails = ClubEvent.builder()
                    .title(eventRequest.getTitle())
                    .description(eventRequest.getDescription())
                    .startTime(eventRequest.getStartTime())
                    .endTime(eventRequest.getEndTime())
                    .location(eventRequest.getLocation())
                    .build();
            ClubEvent updated = clubEventService.updateEvent(id, eventDetails, userId);
            return ResponseEntity.ok(mapToClubEventResponse(updated));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Xóa sự kiện.
     * DELETE /api/events/{id}?userId=1
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteEvent(@PathVariable Integer id,
                                         @RequestParam Integer userId) {
        try {
            clubEventService.deleteEvent(id, userId);
            return ResponseEntity.ok("Xóa sự kiện thành công!");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Lấy danh sách sự kiện của một Câu lạc bộ.
     * GET /api/events/club/{clubId}
     */
    @GetMapping("/club/{clubId}")
    public ResponseEntity<List<ClubEventResponse>> getEventsByClub(@PathVariable Integer clubId) {
        List<ClubEventResponse> responses = clubEventService.getEventsByClubId(clubId).stream()
                .map(this::mapToClubEventResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Lấy thông tin chi tiết sự kiện theo ID.
     * GET /api/events/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getEventById(@PathVariable Integer id) {
        try {
            ClubEvent event = clubEventService.getEventById(id);
            return ResponseEntity.ok(mapToClubEventResponse(event));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Xem trạng thái đồng bộ Google Calendar của sự kiện.
     * GET /api/events/{id}/sync-status
     */
    @GetMapping("/{id}/sync-status")
    public ResponseEntity<?> getSyncStatus(@PathVariable Integer id) {
        Optional<EventGoogleSync> syncOpt = clubEventService.getSyncInfo(id);
        if (syncOpt.isPresent()) {
            return ResponseEntity.ok(syncOpt.get());
        }
        return ResponseEntity.notFound().build();
    }

    private ClubEventResponse mapToClubEventResponse(ClubEvent event) {
        if (event == null) return null;
        
        Optional<EventGoogleSync> syncOpt = clubEventService.getSyncInfo(event.getId());
        String googleEventLink = syncOpt.map(EventGoogleSync::getGoogleEventLink).orElse(null);
        String syncStatus = syncOpt.map(s -> s.getSyncStatus().name()).orElse(null);

        return ClubEventResponse.builder()
                .id(event.getId())
                .clubId(event.getClub() != null ? event.getClub().getId() : null)
                .clubName(event.getClub() != null ? event.getClub().getName() : null)
                .title(event.getTitle())
                .description(event.getDescription())
                .startTime(event.getStartTime())
                .endTime(event.getEndTime())
                .location(event.getLocation())
                .createdByUserId(event.getCreatedBy() != null ? event.getCreatedBy().getUserId() : null)
                .createdByName(event.getCreatedBy() != null ? event.getCreatedBy().getFullName() : null)
                .createdAt(event.getCreatedAt())
                .updatedAt(event.getUpdatedAt())
                .googleEventLink(googleEventLink)
                .syncStatus(syncStatus)
                .build();
    }
}
