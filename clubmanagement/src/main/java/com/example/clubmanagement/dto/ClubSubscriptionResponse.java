package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.SubscriptionStatus;
import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubSubscriptionResponse {
    private Integer id;
    private Integer clubId;
    private String clubName;
    private Integer packageId;
    private String packageName;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private SubscriptionStatus status;
}
