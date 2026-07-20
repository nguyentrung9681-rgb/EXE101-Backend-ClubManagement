package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.SyncStatus;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ClubTaskService {

    @Autowired private ClubTaskRepository taskRepository;
    @Autowired private ClubTrelloConfigRepository trelloConfigRepository;
    @Autowired private TrelloService trelloService;
    @Autowired private ClubRepository clubRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ClubEventRepository eventRepository;
    @Autowired private ClubMemberRepository clubMemberRepository;

    public ClubTask createInternalTask(ClubTask task, Integer clubId) {
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new RuntimeException("Club not found"));
        task.setClub(club);
        task.setSyncStatus(SyncStatus.PENDING);
        ClubTask savedTask = taskRepository.save(task);

        Optional<ClubTrelloConfig> configOpt = trelloConfigRepository.findByClubId(clubId);
        if (configOpt.isPresent()) {
            ClubTrelloConfig config = configOpt.get();
            try {
                Map<String, Object> card = trelloService.createCard(config.getTrelloToken(), config.getTodoListId(), savedTask);
                if (card != null) {
                    savedTask.setTrelloCardId(card.get("id").toString());
                    savedTask.setTrelloCardUrl(card.get("shortUrl").toString());
                    savedTask.setTrelloBoardId(config.getTrelloBoardId());
                    savedTask.setTrelloListId(config.getTodoListId());
                    savedTask.setSyncStatus(SyncStatus.SYNCED);
                    taskRepository.save(savedTask);
                }
            } catch (Exception e) {
                savedTask.setSyncStatus(SyncStatus.FAILED);
                taskRepository.save(savedTask);
            }
        }
        return savedTask;
    }

    public ClubTaskResponse createTask(ClubTaskRequest req, Integer clubId, Integer userId) {
        // Validate creator is a member of the club
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        Club club = clubRepository.findById(clubId)
                .orElseThrow(() -> new RuntimeException("Club not found"));

        ClubTask task = ClubTask.builder()
                .club(club)
                .title(req.getTitle())
                .description(req.getDescription())
                .status(req.getStatus() != null ? req.getStatus() : "TODO")
                .dueDate(req.getDueDate())
                .department(req.getDepartment())
                .isFinanceRelated(req.isFinanceRelated())
                .isEventCritical(req.isEventCritical())
                .syncStatus(SyncStatus.PENDING)
                .build();

        if (req.getEventId() != null) {
            ClubEvent event = eventRepository.findById(req.getEventId())
                    .orElseThrow(() -> new RuntimeException("Event not found"));
            task.setEvent(event);
        }

        if (req.getAssignedUserId() != null) {
            User user = userRepository.findById(req.getAssignedUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            task.setAssignedUser(user);
        }

        ClubTask savedTask = taskRepository.save(task);

        // Sync with Trello if config exists
        Optional<ClubTrelloConfig> configOpt = trelloConfigRepository.findByClubId(clubId);
        if (configOpt.isPresent()) {
            ClubTrelloConfig config = configOpt.get();
            if (config.getTrelloToken() != null && config.getTrelloBoardId() != null) {
                try {
                    String listId = config.getTodoListId();
                    if ("DOING".equalsIgnoreCase(task.getStatus())) {
                        listId = config.getDoingListId();
                    } else if ("DONE".equalsIgnoreCase(task.getStatus())) {
                        listId = config.getDoneListId();
                    }

                    Map<String, Object> card = trelloService.createCard(config.getTrelloToken(), listId, savedTask);
                    if (card != null) {
                        savedTask.setTrelloCardId(card.get("id").toString());
                        savedTask.setTrelloCardUrl(card.get("shortUrl").toString());
                        savedTask.setTrelloBoardId(config.getTrelloBoardId());
                        savedTask.setTrelloListId(listId);
                        savedTask.setSyncStatus(SyncStatus.SYNCED);
                        savedTask = taskRepository.save(savedTask);
                    }
                } catch (Exception e) {
                    savedTask.setSyncStatus(SyncStatus.FAILED);
                    savedTask = taskRepository.save(savedTask);
                }
            }
        }

        return mapToResponse(savedTask);
    }

    public List<ClubTaskResponse> getTasks(Integer clubId, Integer userId, String filterType, Integer eventId, String department, String status) {
        // Verify user belongs to the club
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        List<ClubTask> tasks;
        if ("my-tasks".equalsIgnoreCase(filterType)) {
            tasks = taskRepository.findByAssignedUserUserId(userId);
            tasks.removeIf(t -> !t.getClub().getId().equals(clubId));
        } else if ("finance".equalsIgnoreCase(filterType)) {
            tasks = taskRepository.findByClubIdAndIsFinanceRelatedTrue(clubId);
        } else if ("event".equalsIgnoreCase(filterType) && eventId != null) {
            tasks = taskRepository.findByEventId(eventId);
        } else {
            tasks = taskRepository.findByClubId(clubId);
        }

        if (department != null && !department.isEmpty()) {
            tasks.removeIf(t -> t.getDepartment() == null || !t.getDepartment().equalsIgnoreCase(department));
        }
        if (status != null && !status.isEmpty()) {
            tasks.removeIf(t -> t.getStatus() == null || !t.getStatus().equalsIgnoreCase(status));
        }

        List<ClubTaskResponse> responses = new ArrayList<>();
        for (ClubTask t : tasks) {
            responses.add(mapToResponse(t));
        }
        return responses;
    }

    public ClubTaskResponse updateTask(Integer taskId, ClubTaskRequest req, Integer userId) {
        ClubTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Integer clubId = task.getClub().getId();
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        task.setTitle(req.getTitle());
        task.setDescription(req.getDescription());
        task.setStatus(req.getStatus());
        task.setDueDate(req.getDueDate());
        task.setDepartment(req.getDepartment());
        task.setFinanceRelated(req.isFinanceRelated());
        task.setEventCritical(req.isEventCritical());

        if (req.getEventId() != null) {
            ClubEvent event = eventRepository.findById(req.getEventId())
                    .orElseThrow(() -> new RuntimeException("Event not found"));
            task.setEvent(event);
        } else {
            task.setEvent(null);
        }

        if (req.getAssignedUserId() != null) {
            User user = userRepository.findById(req.getAssignedUserId())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            task.setAssignedUser(user);
        } else {
            task.setAssignedUser(null);
        }

        ClubTask savedTask = taskRepository.save(task);

        if (savedTask.getTrelloCardId() != null) {
            Optional<ClubTrelloConfig> configOpt = trelloConfigRepository.findByClubId(clubId);
            if (configOpt.isPresent()) {
                ClubTrelloConfig config = configOpt.get();
                try {
                    String listId = config.getTodoListId();
                    if ("DOING".equalsIgnoreCase(savedTask.getStatus())) {
                        listId = config.getDoingListId();
                    } else if ("DONE".equalsIgnoreCase(savedTask.getStatus())) {
                        listId = config.getDoneListId();
                    }

                    trelloService.updateCard(config.getTrelloToken(), savedTask.getTrelloCardId(), savedTask, listId);
                    savedTask.setTrelloListId(listId);
                    savedTask.setSyncStatus(SyncStatus.SYNCED);
                    savedTask = taskRepository.save(savedTask);
                } catch (Exception e) {
                    savedTask.setSyncStatus(SyncStatus.FAILED);
                    savedTask = taskRepository.save(savedTask);
                }
            }
        }

        return mapToResponse(savedTask);
    }

    public void deleteTask(Integer taskId, Integer userId) {
        ClubTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Task not found"));

        Integer clubId = task.getClub().getId();
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        if (task.getTrelloCardId() != null) {
            Optional<ClubTrelloConfig> configOpt = trelloConfigRepository.findByClubId(clubId);
            if (configOpt.isPresent()) {
                ClubTrelloConfig config = configOpt.get();
                try {
                    trelloService.deleteCard(config.getTrelloToken(), task.getTrelloCardId());
                } catch (Exception e) {
                    // Ignore trello sync deletion failure
                }
            }
        }

        taskRepository.delete(task);
    }

    public ClubProgressDashboardResponse getDashboardStats(Integer clubId, Integer userId) {
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        List<ClubTask> tasks = taskRepository.findByClubId(clubId);
        long total = tasks.size();
        long todo = tasks.stream().filter(t -> "TODO".equalsIgnoreCase(t.getStatus())).count();
        long doing = tasks.stream().filter(t -> "DOING".equalsIgnoreCase(t.getStatus())).count();
        long done = tasks.stream().filter(t -> "DONE".equalsIgnoreCase(t.getStatus())).count();
        long finance = tasks.stream().filter(ClubTask::isFinanceRelated).count();
        long critical = tasks.stream().filter(ClubTask::isEventCritical).count();

        long criticalDone = tasks.stream().filter(t -> t.isEventCritical() && "DONE".equalsIgnoreCase(t.getStatus())).count();
        double progressPercent = critical == 0 ? 0.0 : ((double) criticalDone / critical) * 100.0;

        LocalDateTime now = LocalDateTime.now();
        long overdue = tasks.stream().filter(t -> t.getDueDate() != null && t.getDueDate().isBefore(now) && !"DONE".equalsIgnoreCase(t.getStatus())).count();

        return ClubProgressDashboardResponse.builder()
                .totalTasks(total)
                .todoTasks(todo)
                .doingTasks(doing)
                .doneTasks(done)
                .financeRelatedTasks(finance)
                .eventCriticalTasks(critical)
                .eventProgressPercentage(progressPercent)
                .overdueTasksCount(overdue)
                .build();
    }

    public List<ClubTaskResponse> getOverdueAndUpcomingReminders(Integer clubId, Integer userId) {
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        List<ClubTask> tasks = taskRepository.findByClubId(clubId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.plusHours(48);

        List<ClubTaskResponse> reminders = new ArrayList<>();
        for (ClubTask t : tasks) {
            if ("DONE".equalsIgnoreCase(t.getStatus())) {
                continue;
            }
            if (t.getDueDate() != null && (t.getDueDate().isBefore(now) || t.getDueDate().isBefore(threshold))) {
                reminders.add(mapToResponse(t));
            }
        }
        return reminders;
    }

    public List<MemberPerformanceResponse> getMemberPerformanceReport(Integer clubId, Integer userId) {
        clubMemberRepository.findByClubIdAndUserUserId(clubId, userId)
                .orElseThrow(() -> new RuntimeException("Access denied: You are not a member of this club"));

        List<ClubMember> members = clubMemberRepository.findByClubId(clubId);
        List<ClubTask> allTasks = taskRepository.findByClubId(clubId);
        LocalDateTime now = LocalDateTime.now();

        List<MemberPerformanceResponse> reports = new ArrayList<>();
        for (ClubMember cm : members) {
            User u = cm.getUser();
            if (u == null) continue;

            long assigned = allTasks.stream().filter(t -> t.getAssignedUser() != null && t.getAssignedUser().getUserId().equals(u.getUserId())).count();
            long completed = allTasks.stream().filter(t -> t.getAssignedUser() != null && t.getAssignedUser().getUserId().equals(u.getUserId()) && "DONE".equalsIgnoreCase(t.getStatus())).count();
            long doing = allTasks.stream().filter(t -> t.getAssignedUser() != null && t.getAssignedUser().getUserId().equals(u.getUserId()) && "DOING".equalsIgnoreCase(t.getStatus())).count();
            long overdue = allTasks.stream().filter(t -> t.getAssignedUser() != null && t.getAssignedUser().getUserId().equals(u.getUserId()) && t.getDueDate() != null && t.getDueDate().isBefore(now) && !"DONE".equalsIgnoreCase(t.getStatus())).count();

            reports.add(MemberPerformanceResponse.builder()
                    .userId(u.getUserId())
                    .fullName(u.getFullName())
                    .email(u.getEmail())
                    .assignedTasksCount(assigned)
                    .completedTasksCount(completed)
                    .inProgressTasksCount(doing)
                    .overdueTasksCount(overdue)
                    .build());
        }
        return reports;
    }

    public void processWebhook(Map<String, Object> payload) {
        if (!payload.containsKey("action")) return;
        Map<String, Object> action = (Map<String, Object>) payload.get("action");
        String type = action.get("type").toString();
        Map<String, Object> data = (Map<String, Object>) action.get("data");

        if (data == null || !data.containsKey("card")) return;
        Map<String, Object> cardData = (Map<String, Object>) data.get("card");
        String cardId = cardData.get("id").toString();

        Optional<ClubTask> taskOpt = taskRepository.findByTrelloCardId(cardId);
        if (!taskOpt.isPresent()) return;
        ClubTask task = taskOpt.get();

        ClubTrelloConfig config = trelloConfigRepository.findByClubId(task.getClub().getId())
                .orElseThrow(() -> new RuntimeException("Trello configuration not found"));

        if ("updateCard".equals(type)) {
            if (cardData.containsKey("name")) task.setTitle(cardData.get("name").toString());
            if (cardData.containsKey("desc")) task.setDescription(cardData.get("desc").toString());

            // Check if card has a new due date in update action
            if (action.containsKey("display")) {
                Map<String, Object> display = (Map<String, Object>) action.get("display");
                if (display.containsKey("translationKey")) {
                    String translationKey = display.get("translationKey").toString();
                    if ("action_changed_date_of_card".equals(translationKey)) {
                        // Card due date changed
                        if (cardData.containsKey("due")) {
                            Object dueObj = cardData.get("due");
                            if (dueObj != null) {
                                try {
                                    task.setDueDate(LocalDateTime.parse(dueObj.toString().substring(0, 19)));
                                } catch (Exception e) {
                                    // ignore date parse errors
                                }
                            } else {
                                task.setDueDate(null);
                            }
                        }
                    }
                }
            }

            if (data.containsKey("listAfter")) {
                Map<String, Object> listAfter = (Map<String, Object>) data.get("listAfter");
                String listId = listAfter.get("id").toString();
                task.setTrelloListId(listId);

                if (listId.equals(config.getTodoListId())) task.setStatus("TODO");
                else if (listId.equals(config.getDoingListId())) task.setStatus("DOING");
                else if (listId.equals(config.getDoneListId())) task.setStatus("DONE");
            }
            taskRepository.save(task);
        }
    }

    private ClubTaskResponse mapToResponse(ClubTask t) {
        return ClubTaskResponse.builder()
                .taskId(t.getTaskId())
                .clubId(t.getClub().getId())
                .clubName(t.getClub().getName())
                .eventId(t.getEvent() != null ? t.getEvent().getId() : null)
                .eventTitle(t.getEvent() != null ? t.getEvent().getTitle() : null)
                .assignedUserId(t.getAssignedUser() != null ? t.getAssignedUser().getUserId() : null)
                .assignedUserName(t.getAssignedUser() != null ? t.getAssignedUser().getFullName() : null)
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .dueDate(t.getDueDate())
                .department(t.getDepartment())
                .isFinanceRelated(t.isFinanceRelated())
                .isEventCritical(t.isEventCritical())
                .trelloBoardId(t.getTrelloBoardId())
                .trelloListId(t.getTrelloListId())
                .trelloCardId(t.getTrelloCardId())
                .trelloCardUrl(t.getTrelloCardUrl())
                .syncStatus(t.getSyncStatus() != null ? t.getSyncStatus().name() : null)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}