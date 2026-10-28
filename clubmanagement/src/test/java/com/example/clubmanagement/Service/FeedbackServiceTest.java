package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Feedback;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.FeedbackRepository;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.FeedbackRatingOption;
import com.example.clubmanagement.dto.FeedbackRequest;
import com.example.clubmanagement.dto.FeedbackResponse;
import com.example.clubmanagement.dto.FeedbackSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeedbackServiceTest {

    @Mock
    private FeedbackRepository feedbackRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FeedbackService feedbackService;

    private User sampleUser;
    private Feedback sampleFeedback;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        sampleUser = User.builder()
                .userId(1)
                .fullName("Admin User")
                .email("admin@example.com")
                .avatarUrl("http://avatar.url")
                .build();

        sampleFeedback = Feedback.builder()
                .id(10)
                .user(sampleUser)
                .rating((short) 5)
                .comment("Hệ thống rất tốt!")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Admin lấy danh sách feedback có phân trang thành công")
    void getAllFeedbacks_Success() {
        Page<Feedback> feedbackPage = new PageImpl<>(List.of(sampleFeedback));
        when(feedbackRepository.findAllBy(any(Pageable.class))).thenReturn(feedbackPage);

        Page<FeedbackResponse> result = feedbackService.getAllFeedbacks(null, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals(5, result.getContent().get(0).getRating());
        assertEquals("Rất tốt", result.getContent().get(0).getRatingLabel());
        assertEquals("Hệ thống rất tốt!", result.getContent().get(0).getComment());
        assertEquals("Admin User", result.getContent().get(0).getFullName());
        verify(feedbackRepository).findAllBy(any(Pageable.class));
    }

    @Test
    @DisplayName("Admin lọc danh sách feedback theo rating")
    void getAllFeedbacks_FilterByRating_Success() {
        Page<Feedback> feedbackPage = new PageImpl<>(List.of(sampleFeedback));
        when(feedbackRepository.findByRating(eq((short) 5), any(Pageable.class))).thenReturn(feedbackPage);

        Page<FeedbackResponse> result = feedbackService.getAllFeedbacks(5, 0, 10);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        verify(feedbackRepository).findByRating(eq((short) 5), any(Pageable.class));
    }

    @Test
    @DisplayName("Admin lọc danh sách với rating không hợp lệ ném lỗi")
    void getAllFeedbacks_InvalidRating_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> feedbackService.getAllFeedbacks(6, 0, 10));
    }

    @Test
    @DisplayName("Admin lấy thống kê feedback tổng quan (summary)")
    void getSummary_Success() {
        when(feedbackRepository.countGroupByRating()).thenReturn(List.<Object[]>of(
                new Object[]{(short) 5, 10L},
                new Object[]{(short) 4, 5L}
        ));
        when(feedbackRepository.findAverageRating()).thenReturn(4.67);

        FeedbackSummaryResponse summary = feedbackService.getSummary();

        assertNotNull(summary);
        assertEquals(15L, summary.getTotalFeedbacks());
        assertEquals(4.67, summary.getAverageRating());
        assertEquals(10L, summary.getRatingCounts().get(5));
        assertEquals(5L, summary.getRatingCounts().get(4));
        assertEquals(0L, summary.getRatingCounts().get(1));
    }

    @Test
    @DisplayName("Admin xoá feedback tồn tại thành công")
    void deleteFeedbackById_Success() {
        when(feedbackRepository.existsById(10)).thenReturn(true);

        assertDoesNotThrow(() -> feedbackService.deleteFeedbackById(10));
        verify(feedbackRepository).deleteById(10);
    }

    @Test
    @DisplayName("Admin xoá feedback không tồn tại ném NoSuchElementException")
    void deleteFeedbackById_NotFound() {
        when(feedbackRepository.existsById(999)).thenReturn(false);

        assertThrows(NoSuchElementException.class, () -> feedbackService.deleteFeedbackById(999));
        verify(feedbackRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("Lấy danh sách các mức sao để lựa chọn")
    void getRatingOptions_Success() {
        List<FeedbackRatingOption> options = feedbackService.getRatingOptions();
        assertNotNull(options);
        assertEquals(5, options.size());
        assertEquals(1, options.get(0).getValue());
        assertEquals(5, options.get(4).getValue());
    }
}

