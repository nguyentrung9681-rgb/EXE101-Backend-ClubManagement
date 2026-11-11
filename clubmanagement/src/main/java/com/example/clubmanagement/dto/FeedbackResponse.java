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

    /** true khi review gắn với một đơn đã thanh toán (verified review). */
    private boolean verified;
    /** Mã đơn đã thanh toán mà review này gắn vào, null nếu chưa xác minh. */
    private Long orderCode;
}
