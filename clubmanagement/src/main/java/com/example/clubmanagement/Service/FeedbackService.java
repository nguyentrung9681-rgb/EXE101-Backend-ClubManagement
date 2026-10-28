package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Feedback;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.FeedbackRating;
import com.example.clubmanagement.dto.FeedbackRatingOption;
import com.example.clubmanagement.Repository.FeedbackRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.FeedbackRequest;
import com.example.clubmanagement.dto.FeedbackResponse;
import com.example.clubmanagement.dto.FeedbackSummaryResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class FeedbackService {

    public static final int MIN_RATING = 1;
    public static final int MAX_RATING = 5;
    public static final int MAX_COMMENT_LENGTH = 2000;
    private static final int MAX_PAGE_SIZE = 100;

    private final FeedbackRepository feedbackRepository;
    private final UserRepository userRepository;

    public FeedbackService(FeedbackRepository feedbackRepository, UserRepository userRepository) {
        this.feedbackRepository = feedbackRepository;
        this.userRepository = userRepository;
    }

    // ====================== USER ======================

    /** Danh sách các mức sao (1-5) kèm nhãn để Frontend render nút chọn sao. */
    public List<FeedbackRatingOption> getRatingOptions() {
        return Arrays.stream(FeedbackRating.values())
                .map(r -> new FeedbackRatingOption(r.getValue(), r.getLabel()))
                .toList();
    }

    /** Gửi feedback mới. Mỗi người dùng chỉ được gửi 1 lần (lần sau dùng update). */
    @Transactional
    public FeedbackResponse createFeedback(Integer userId, FeedbackRequest request) {
        validateRequest(request);
        if (feedbackRepository.existsByUser_UserId(userId)) {
            throw new IllegalStateException("Bạn đã gửi feedback rồi. Hãy cập nhật feedback hiện có.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("Không tìm thấy người dùng!"));

        Feedback feedback = Feedback.builder()
                .user(user)
                .rating(request.getRating().shortValue())
                .comment(normalizeComment(request.getComment()))
                .build();
        try {
            // saveAndFlush để INSERT chạy ngay tại đây -> bắt được lỗi vi phạm UNIQUE(user_id)
            // khi 2 request gửi gần như đồng thời cùng vượt qua bước existsByUser_UserId ở trên.
            return toResponse(feedbackRepository.saveAndFlush(feedback));
        } catch (DataIntegrityViolationException e) {
            throw new IllegalStateException("Bạn đã gửi feedback rồi. Hãy cập nhật feedback hiện có.");
        }
    }

    /** Cập nhật feedback của chính mình. */
    @Transactional
    public FeedbackResponse updateMyFeedback(Integer userId, FeedbackRequest request) {
        validateRequest(request);
        Feedback feedback = findByUserOrThrow(userId);
        feedback.setRating(request.getRating().shortValue());
        feedback.setComment(normalizeComment(request.getComment()));
        return toResponse(feedbackRepository.save(feedback));
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getMyFeedback(Integer userId) {
        return toResponse(findByUserOrThrow(userId));
    }

    @Transactional
    public void deleteMyFeedback(Integer userId) {
        feedbackRepository.delete(findByUserOrThrow(userId));
    }

    // ====================== ADMIN ======================

    /** Danh sách feedback (mới nhất trước), có thể lọc theo số sao. */
    @Transactional(readOnly = true)
    public Page<FeedbackResponse> getAllFeedbacks(Integer rating, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Feedback> result;
        if (rating != null) {
            validateRating(rating);
            result = feedbackRepository.findByRating(rating.shortValue(), pageable);
        } else {
            result = feedbackRepository.findAllBy(pageable);
        }
        return result.map(this::toResponse);
    }

    /** Thống kê: tổng số, điểm trung bình, số lượng theo từng mức sao. */
    @Transactional(readOnly = true)
    public FeedbackSummaryResponse getSummary() {
        Map<Integer, Long> counts = new LinkedHashMap<>();
        for (int star = MAX_RATING; star >= MIN_RATING; star--) {
            counts.put(star, 0L);
        }
        long total = 0;
        for (Object[] row : feedbackRepository.countGroupByRating()) {
            int star = ((Number) row[0]).intValue();
            long count = ((Number) row[1]).longValue();
            counts.put(star, count);
            total += count;
        }

        Double avg = feedbackRepository.findAverageRating();
        double roundedAvg = avg == null ? 0.0 : Math.round(avg * 100.0) / 100.0;

        return FeedbackSummaryResponse.builder()
                .totalFeedbacks(total)
                .averageRating(roundedAvg)
                .ratingCounts(counts)
                .build();
    }

    @Transactional
    public void deleteFeedbackById(Integer feedbackId) {
        if (!feedbackRepository.existsById(feedbackId)) {
            throw new NoSuchElementException("Không tìm thấy feedback!");
        }
        feedbackRepository.deleteById(feedbackId);
    }

    // ====================== HELPERS ======================

    private Feedback findByUserOrThrow(Integer userId) {
        return feedbackRepository.findByUser_UserId(userId)
                .orElseThrow(() -> new NoSuchElementException("Bạn chưa gửi feedback nào!"));
    }

    private void validateRequest(FeedbackRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu feedback không được để trống!");
        }
        validateRating(request.getRating());
        if (request.getComment() != null && request.getComment().length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException("Nhận xét tối đa " + MAX_COMMENT_LENGTH + " ký tự!");
        }
    }

    private void validateRating(Integer rating) {
        FeedbackRating.fromValue(rating);
    }

    private String normalizeComment(String comment) {
        if (comment == null) return null;
        String trimmed = comment.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private FeedbackResponse toResponse(Feedback f) {
        User u = f.getUser();
        return FeedbackResponse.builder()
                .id(f.getId())
                .rating(f.getRating() == null ? null : f.getRating().intValue())
                .ratingLabel(f.getRating() == null ? null : FeedbackRating.fromValue(f.getRating().intValue()).getLabel())
                .comment(f.getComment())
                .createdAt(f.getCreatedAt())
                .updatedAt(f.getUpdatedAt())
                .userId(u != null ? u.getUserId() : null)
                .fullName(u != null ? u.getFullName() : null)
                .email(u != null ? u.getEmail() : null)
                .avatarUrl(u != null ? u.getAvatarUrl() : null)
                .build();
    }
}
