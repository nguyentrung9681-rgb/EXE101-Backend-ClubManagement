package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubTaskResponse {
    private Integer taskId;
    private Integer clubId;
    private String clubName;
    private Integer eventId;
    private String eventTitle;
    private Integer assignedUserId;
    private String assignedUserName;
    private String title;
    private String description;
    private String status;
    private LocalDateTime dueDate;
    private String department;
    private boolean isFinanceRelated;
    private boolean isEventCritical;
    private String trelloBoardId;
    private String trelloListId;
    private String trelloCardId;
    private String trelloCardUrl;
    private String syncStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
