package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.MessageType;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessageResponse {
    private Long id;
    private Integer clubId;
    private Integer departmentId;
    private String departmentName;
    private Integer senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private MessageType messageType;
    private Boolean isPinned;
    private Boolean isDeleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<ChatAttachmentDto> attachments;
}
