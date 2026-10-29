package com.example.clubmanagement.dto;

import lombok.*;

/** Một lựa chọn số sao để Frontend render nút chọn. */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FeedbackRatingOption {
    /** Giá trị gửi lên trong trường "rating" (1-5). */
    private int value;
    /** Nhãn hiển thị, VD: "Rất tốt". */
    private String label;
}
