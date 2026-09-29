package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.MessageType;
import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatMessageRequest {
    private String content;
    private MessageType messageType; // TEXT, FILE, SYSTEM
    private List<ChatAttachmentDto> attachments;
    private Boolean isPinned;
}
