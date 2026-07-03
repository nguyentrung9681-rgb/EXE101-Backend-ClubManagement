package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.SyncSource;
import com.example.clubmanagement.Enum.SyncStatus;
import com.example.clubmanagement.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ClubEventService {

    private final ClubEventRepository clubEventRepository;
    private final ClubRepository clubRepository;
    private final UserRepository userRepository;
    private final GoogleAccountRepository googleAccountRepository;
    private final EventGoogleSyncRepository eventGoogleSyncRepository;
    private final GoogleCalendarService googleCalendarService;

    public ClubEventService(ClubEventRepository clubEventRepository,
                            ClubRepository clubRepository,
                            UserRepository userRepository,
                            GoogleAccountRepository googleAccountRepository,
                            EventGoogleSyncRepository eventGoogleSyncRepository,
                            GoogleCalendarService googleCalendarService) {
        this.clubEventRepository = clubEventRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.googleAccountRepository = googleAccountRepository;
        this.eventGoogleSyncRepository = eventGoogleSyncRepository;
        this.googleCalendarService = googleCalendarService;
    }

    /**
     * Tạo một sự kiện CLB mới và tự động đồng bộ lên Google Calendar nếu người tạo đã liên kết tài khoản.
     */
    @Transactional
    public ClubEvent createEvent(ClubEvent event, Integer clubId, Integer userId) {
        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy Câu lạc bộ!"));
        User creator = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        event.setClub(club);
        event.setCreatedBy(creator);
        ClubEvent savedEvent = clubEventRepository.save(event);

        // Khởi tạo bản ghi đồng bộ mặc định (PENDING)
        EventGoogleSync sync = EventGoogleSync.builder()
                .clubEvent(savedEvent)
                .syncStatus(SyncStatus.PENDING)
                .syncSource(SyncSource.LOCAL)
                .build();

        // Kiểm tra xem người tạo đã liên kết Google Account chưa
        Optional<GoogleAccount> googleAccountOpt = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId);
        if (googleAccountOpt.isPresent()) {
            GoogleAccount account = googleAccountOpt.get();
            sync.setGoogleCalendarId("primary");
            try {
                Map<String, String> googleResult = googleCalendarService.createEvent(savedEvent, account);
                sync.setGoogleEventId(googleResult.get("googleEventId"));
                sync.setGoogleEventLink(googleResult.get("googleEventLink"));
                sync.setGoogleEtag(googleResult.get("googleEtag"));
                sync.setSyncStatus(SyncStatus.SYNCED);
                sync.setLastSyncedAt(LocalDateTime.now());
            } catch (Exception e) {
                sync.setSyncStatus(SyncStatus.FAILED);
                sync.setLastSyncError("Không thể tạo sự kiện trên Google Calendar: " + e.getMessage());
            }
        } else {
            sync.setLastSyncError("Người dùng chưa kết nối tài khoản Google.");
        }

        eventGoogleSyncRepository.save(sync);
        return savedEvent;
    }

    /**
     * Cập nhật sự kiện và đồng bộ thay đổi lên Google Calendar.
     */
    @Transactional
    public ClubEvent updateEvent(Integer eventId, ClubEvent updatedDetails, Integer userId) {
        ClubEvent event = clubEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện!"));

        event.setTitle(updatedDetails.getTitle());
        event.setDescription(updatedDetails.getDescription());
        event.setStartTime(updatedDetails.getStartTime());
        event.setEndTime(updatedDetails.getEndTime());
        event.setLocation(updatedDetails.getLocation());
        ClubEvent savedEvent = clubEventRepository.save(event);

        // Tìm bản ghi đồng bộ
        EventGoogleSync sync = eventGoogleSyncRepository.findByClubEventId(eventId)
                .orElse(EventGoogleSync.builder()
                        .clubEvent(savedEvent)
                        .syncStatus(SyncStatus.PENDING)
                        .syncSource(SyncSource.LOCAL)
                        .build());

        Optional<GoogleAccount> googleAccountOpt = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId);
        if (googleAccountOpt.isPresent()) {
            GoogleAccount account = googleAccountOpt.get();
            sync.setGoogleCalendarId("primary");
            try {
                // Nếu chưa từng sync thành công (hoặc googleEventId trống), gọi tạo mới trên Google Calendar
                if (sync.getGoogleEventId() == null) {
                    Map<String, String> googleResult = googleCalendarService.createEvent(savedEvent, account);
                    sync.setGoogleEventId(googleResult.get("googleEventId"));
                    sync.setGoogleEventLink(googleResult.get("googleEventLink"));
                    sync.setGoogleEtag(googleResult.get("googleEtag"));
                } else {
                    // Nếu đã có googleEventId, gọi API cập nhật
                    Map<String, String> googleResult = googleCalendarService.updateEvent(savedEvent, sync, account);
                    sync.setGoogleEtag(googleResult.get("googleEtag"));
                }
                sync.setSyncStatus(SyncStatus.SYNCED);
                sync.setLastSyncedAt(LocalDateTime.now());
                sync.setLastSyncError(null);
            } catch (Exception e) {
                sync.setSyncStatus(SyncStatus.FAILED);
                sync.setLastSyncError("Không thể cập nhật sự kiện trên Google Calendar: " + e.getMessage());
            }
        } else {
            sync.setLastSyncError("Người dùng chưa kết nối tài khoản Google.");
        }

        eventGoogleSyncRepository.save(sync);
        return savedEvent;
    }

    /**
     * Xóa sự kiện và xóa tương ứng trên Google Calendar.
     */
    @Transactional
    public void deleteEvent(Integer eventId, Integer userId) {
        ClubEvent event = clubEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện!"));

        Optional<EventGoogleSync> syncOpt = eventGoogleSyncRepository.findByClubEventId(eventId);
        if (syncOpt.isPresent()) {
            EventGoogleSync sync = syncOpt.get();
            Optional<GoogleAccount> googleAccountOpt = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId);
            if (googleAccountOpt.isPresent() && sync.getGoogleEventId() != null) {
                try {
                    googleCalendarService.deleteEvent(sync, googleAccountOpt.get());
                } catch (Exception e) {
                    // Log lỗi nhưng vẫn tiếp tục xóa trong DB để tránh kẹt dữ liệu
                    System.err.println("Lỗi khi xóa sự kiện trên Google Calendar: " + e.getMessage());
                }
            }
            eventGoogleSyncRepository.delete(sync);
        }

        clubEventRepository.delete(event);
    }

    public List<ClubEvent> getEventsByClubId(Integer clubId) {
        return clubEventRepository.findByClubId(clubId);
    }

    public ClubEvent getEventById(Integer eventId) {
        return clubEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện!"));
    }

    public Optional<EventGoogleSync> getSyncInfo(Integer eventId) {
        return eventGoogleSyncRepository.findByClubEventId(eventId);
    }
}
