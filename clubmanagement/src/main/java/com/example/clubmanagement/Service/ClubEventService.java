package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.SyncSource;
import com.example.clubmanagement.Enum.SyncStatus;
import com.example.clubmanagement.Repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.DayOfWeek;
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
    private final ClubMemberRepository clubMemberRepository;
    private final ClubTaskRepository clubTaskRepository;
    private final ClubDocumentRepository clubDocumentRepository;

    public ClubEventService(ClubEventRepository clubEventRepository,
                            ClubRepository clubRepository,
                            UserRepository userRepository,
                            GoogleAccountRepository googleAccountRepository,
                            EventGoogleSyncRepository eventGoogleSyncRepository,
                            GoogleCalendarService googleCalendarService,
                            ClubMemberRepository clubMemberRepository,
                            ClubTaskRepository clubTaskRepository,
                            ClubDocumentRepository clubDocumentRepository) {
        this.clubEventRepository = clubEventRepository;
        this.clubRepository = clubRepository;
        this.userRepository = userRepository;
        this.googleAccountRepository = googleAccountRepository;
        this.eventGoogleSyncRepository = eventGoogleSyncRepository;
        this.googleCalendarService = googleCalendarService;
        this.clubMemberRepository = clubMemberRepository;
        this.clubTaskRepository = clubTaskRepository;
        this.clubDocumentRepository = clubDocumentRepository;
    }

    /**
     * Tạo một sự kiện CLB mới và tự động đồng bộ lên Google Calendar nếu người tạo đã liên kết tài khoản.
     */
    @Transactional
    public ClubEvent createEvent(ClubEvent event, Integer clubId, Integer userId) {
        // Kiểm tra vai trò của người dùng trong CLB
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của câu lạc bộ này!"));
        if (member.getRole() != ClubMemberRole.PRESIDENT) {
            throw new RuntimeException("Bạn không có quyền tạo sự kiện cho câu lạc bộ này!");
        }

        // Kiểm tra ngày đã qua
        if (event.getStartTime() == null || event.getStartTime().toLocalDate().isBefore(LocalDate.now())) {
            throw new RuntimeException("Không thể tạo lịch sự kiện cho ngày đã qua!");
        }
        event.setStatus("ONGOING");

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

                // Lưu link Google Meet vào sự kiện
                if (googleResult.containsKey("googleMeetLink") && !googleResult.get("googleMeetLink").isEmpty()) {
                    savedEvent.setMeetLink(googleResult.get("googleMeetLink"));
                    clubEventRepository.save(savedEvent);
                }
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

        // Kiểm tra vai trò của người dùng trong CLB sở hữu sự kiện
        Integer clubId = event.getClub().getId();
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của câu lạc bộ này!"));
        if (member.getRole() != ClubMemberRole.PRESIDENT) {
            throw new RuntimeException("Bạn không có quyền chỉnh sửa sự kiện của câu lạc bộ này!");
        }

        checkAndUpdateEventStatus(event);
        if ("ENDED".equals(event.getStatus())) {
            throw new RuntimeException("Sự kiện đã kết thúc, không thể chỉnh sửa!");
        }

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

                    if (googleResult.containsKey("googleMeetLink") && !googleResult.get("googleMeetLink").isEmpty()) {
                        savedEvent.setMeetLink(googleResult.get("googleMeetLink"));
                        clubEventRepository.save(savedEvent);
                    }
                } else {
                    // Nếu đã có googleEventId, gọi API cập nhật
                    Map<String, String> googleResult = googleCalendarService.updateEvent(savedEvent, sync, account);
                    sync.setGoogleEtag(googleResult.get("googleEtag"));

                    if (googleResult.containsKey("googleMeetLink") && !googleResult.get("googleMeetLink").isEmpty()) {
                        savedEvent.setMeetLink(googleResult.get("googleMeetLink"));
                        clubEventRepository.save(savedEvent);
                    }
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

        // Kiểm tra vai trò của người dùng trong CLB sở hữu sự kiện
        Integer clubId = event.getClub().getId();
        ClubMember member = clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Bạn không phải thành viên của câu lạc bộ này!"));
        if (member.getRole() != ClubMemberRole.PRESIDENT) {
            throw new RuntimeException("Bạn không có quyền xóa sự kiện của câu lạc bộ này!");
        }

        checkAndUpdateEventStatus(event);
        if ("ENDED".equals(event.getStatus())) {
            throw new RuntimeException("Sự kiện đã kết thúc, không thể xóa!");
        }

        // Bỏ liên kết sự kiện ở các công việc (ClubTask)
        List<ClubTask> tasks = clubTaskRepository.findByEventId(eventId);
        if (tasks != null && !tasks.isEmpty()) {
            for (ClubTask task : tasks) {
                task.setEvent(null);
            }
            clubTaskRepository.saveAll(tasks);
        }

        // Bỏ liên kết sự kiện ở các tài liệu (ClubDocument)
        List<ClubDocument> documents = clubDocumentRepository.findByEventId(eventId);
        if (documents != null && !documents.isEmpty()) {
            for (ClubDocument doc : documents) {
                doc.setEvent(null);
            }
            clubDocumentRepository.saveAll(documents);
        }

        // Xóa đồng bộ trên Google Calendar nếu có
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

        // Xóa cứng sự kiện khỏi database
        clubEventRepository.delete(event);
    }

    public List<ClubEvent> getEventsByClubId(Integer clubId) {
        List<ClubEvent> events = clubEventRepository.findByClubId(clubId);
        events.forEach(this::checkAndUpdateEventStatus);
        return events;
    }

    public ClubEvent getEventById(Integer eventId) {
        ClubEvent event = clubEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện!"));
        checkAndUpdateEventStatus(event);
        return event;
    }

    public Optional<EventGoogleSync> getSyncInfo(Integer eventId) {
        return eventGoogleSyncRepository.findByClubEventId(eventId);
    }

    /**
     * Lọc sự kiện theo khoảng thời gian: ngày, tuần, hoặc tháng.
     */
    public List<ClubEvent> getFilteredEvents(Integer clubId, String viewType, LocalDate date) {
        if (date == null) {
            date = LocalDate.now();
        }

        LocalDateTime start;
        LocalDateTime end;

        switch (viewType.toLowerCase()) {
            case "day":
                start = date.atStartOfDay();
                end = date.atTime(LocalTime.MAX);
                break;
            case "week":
                // Tuần bắt đầu từ Thứ Hai
                DayOfWeek dayOfWeek = date.getDayOfWeek();
                int daysToMonday = dayOfWeek.getValue() - DayOfWeek.MONDAY.getValue();
                LocalDate monday = date.minusDays(daysToMonday);
                LocalDate sunday = monday.plusDays(6);

                start = monday.atStartOfDay();
                end = sunday.atTime(LocalTime.MAX);
                break;
            case "month":
                LocalDate firstDay = date.withDayOfMonth(1);
                LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());

                start = firstDay.atStartOfDay();
                end = lastDay.atTime(LocalTime.MAX);
                break;
            default:
                throw new IllegalArgumentException("viewType không hợp lệ! Chỉ chấp nhận: day, week, month");
        }

        List<ClubEvent> events;
        if (clubId != null) {
            events = clubEventRepository.findByClubIdAndStartTimeBetween(clubId, start, end);
        } else {
            events = clubEventRepository.findByStartTimeBetween(start, end);
        }
        events.forEach(this::checkAndUpdateEventStatus);
        return events;
    }

    /**
     * Đồng bộ thủ công một sự kiện lên Google Calendar (nếu trước đó bị lỗi hoặc chưa sync).
     */
    @Transactional
    public ClubEvent syncEventToGoogle(Integer eventId, Integer userId) {
        ClubEvent event = clubEventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy sự kiện!"));

        GoogleAccount account = googleAccountRepository.findFirstByUserUserIdOrderByCreatedAtDesc(userId)
                .orElseThrow(() -> new RuntimeException("Người dùng chưa liên kết tài khoản Google."));

        EventGoogleSync sync = eventGoogleSyncRepository.findByClubEventId(eventId)
                .orElse(EventGoogleSync.builder()
                        .clubEvent(event)
                        .syncStatus(SyncStatus.PENDING)
                        .syncSource(SyncSource.LOCAL)
                        .build());

        sync.setGoogleCalendarId("primary");
        try {
            if (sync.getGoogleEventId() == null) {
                Map<String, String> googleResult = googleCalendarService.createEvent(event, account);
                sync.setGoogleEventId(googleResult.get("googleEventId"));
                sync.setGoogleEventLink(googleResult.get("googleEventLink"));
                sync.setGoogleEtag(googleResult.get("googleEtag"));

                if (googleResult.containsKey("googleMeetLink") && !googleResult.get("googleMeetLink").isEmpty()) {
                    event.setMeetLink(googleResult.get("googleMeetLink"));
                    clubEventRepository.save(event);
                }
            } else {
                Map<String, String> googleResult = googleCalendarService.updateEvent(event, sync, account);
                sync.setGoogleEtag(googleResult.get("googleEtag"));

                if (googleResult.containsKey("googleMeetLink") && !googleResult.get("googleMeetLink").isEmpty()) {
                    event.setMeetLink(googleResult.get("googleMeetLink"));
                    clubEventRepository.save(event);
                }
            }
            sync.setSyncStatus(SyncStatus.SYNCED);
            sync.setLastSyncedAt(LocalDateTime.now());
            sync.setLastSyncError(null);
        } catch (Exception e) {
            sync.setSyncStatus(SyncStatus.FAILED);
            sync.setLastSyncError("Đồng bộ thủ công thất bại: " + e.getMessage());
        }

        eventGoogleSyncRepository.save(sync);
        return event;
    }
    private void checkAndUpdateEventStatus(ClubEvent event) {
        String currentStatus = event.getStatus();
        if (currentStatus == null) {
            currentStatus = "ONGOING";
            event.setStatus(currentStatus);
        }
        if ("ONGOING".equals(currentStatus) && LocalDateTime.now().isAfter(event.getEndTime())) {
            event.setStatus("ENDED");
            clubEventRepository.save(event);
        }
    }
}
