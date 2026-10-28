package com.example.clubmanagement.Enum;

import java.util.Arrays;

/**
 * Các mức sao người dùng có thể chọn khi gửi feedback (1 = tệ nhất, 5 = tốt nhất).
 * Đây là nguồn dữ liệu duy nhất cho danh sách lựa chọn trả về Frontend và cho việc validate.
 */
public enum FeedbackRating {
    ONE_STAR(1, "Rất tệ"),
    TWO_STARS(2, "Tệ"),
    THREE_STARS(3, "Bình thường"),
    FOUR_STARS(4, "Tốt"),
    FIVE_STARS(5, "Rất tốt");

    private final int value;
    private final String label;

    FeedbackRating(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    /** Chuyển số sao (1-5) sang enum, ném IllegalArgumentException nếu không hợp lệ. */
    public static FeedbackRating fromValue(Integer value) {
        if (value == null) {
            throw new IllegalArgumentException("Vui lòng chọn số sao đánh giá!");
        }
        return Arrays.stream(values())
                .filter(r -> r.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Vui lòng chọn số sao từ 1 đến 5!"));
    }
}
