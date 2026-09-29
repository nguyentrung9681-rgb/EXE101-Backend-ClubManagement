package com.example.clubmanagement.dto;

import lombok.*;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class MessageCursorPageResponse {
    private List<ChatMessageResponse> messages;
    private Long nextBeforeMessageId; // ID tin nhắn cũ hơn tiếp theo để client kéo lên load tiếp
    private Boolean hasMore;
    private Integer limit;
}
