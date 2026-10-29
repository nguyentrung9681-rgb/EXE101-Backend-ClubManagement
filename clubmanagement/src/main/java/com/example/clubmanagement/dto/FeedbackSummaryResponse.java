package com.example.clubmanagement.dto;

import lombok.*;

import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class FeedbackSummaryResponse {
    /** Tổng số feedback. */
    private long totalFeedbacks;
    /** Điểm trung bình (làm tròn 2 chữ số), 0 nếu chưa có feedback. */
    private double averageRating;
    /** Số lượng theo từng mức sao: key 1..5 (luôn đủ 5 key). */
    private Map<Integer, Long> ratingCounts;
}
