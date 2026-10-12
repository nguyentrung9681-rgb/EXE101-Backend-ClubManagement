package com.example.clubmanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String type; // NEW_CHAT_MESSAGE, MESSAGE_PINNED, MESSAGE_DELETED
    private String title;
    private String snippet;
    private Integer clubId;
    private Integer departmentId;
    private Long messageId;
    private Integer senderId;
    private String senderName;
    private String senderAvatar;
    private LocalDateTime createdAt;
}

