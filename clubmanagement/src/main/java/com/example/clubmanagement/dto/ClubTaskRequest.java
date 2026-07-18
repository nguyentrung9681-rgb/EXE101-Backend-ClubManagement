package com.example.clubmanagement.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClubTaskRequest {
    private String title;
    private String description;
    private Integer eventId;
    private Integer assignedUserId;
    private String status;
    private LocalDateTime dueDate;
    private String department;
    private boolean isFinanceRelated;
    private boolean isEventCritical;
}
