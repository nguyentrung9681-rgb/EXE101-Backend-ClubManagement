package com.example.clubmanagement.dto;

import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FeedbackRequest {
    /** Số sao: 1 (tệ nhất) - 5 (tốt nhất). Bắt buộc. */
    private Integer rating;
    /** Nhận xét (tuỳ chọn, tối đa 2000 ký tự). */
    private String comment;
}
