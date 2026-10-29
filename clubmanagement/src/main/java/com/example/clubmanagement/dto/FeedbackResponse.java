package com.example.clubmanagement.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FeedbackResponse {
    private Integer id;
    private Integer rating;
    /** Nhãn của mức sao, VD: "Rất tốt". */
    private String ratingLabel;
    private String comment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Thông tin người gửi
    private Integer userId;
    private String fullName;
    private String email;
    private String avatarUrl;
}
