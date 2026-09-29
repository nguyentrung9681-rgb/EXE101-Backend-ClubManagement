package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class ChatAttachmentDto {
    private Long id;
    private String fileUrl;
    private String fileName;
    private Long fileSize;
    private String fileType;
}
