package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminDeleteUserTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    private User sampleUser;
    private User adminUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        sampleUser = User.builder()
                .userId(10)
                .username("user10")
                .fullName("User Ten")
                .build();

        adminUser = User.builder()
                .userId(1)
                .username("admin")
                .fullName("Administrator")
                .build();
    }

    @Test
    @DisplayName("Admin xóa tài khoản người dùng bình thường thành công")
    void deleteUserByAdmin_Success() {
        when(userRepository.findById(10)).thenReturn(Optional.of(sampleUser));
        ClubMember member = ClubMember.builder()
                .role(ClubMemberRole.MEMBER)
                .status(ClubMemberStatus.ACTIVE)
                .build();
        when(clubMemberRepository.findByUserUserId(10)).thenReturn(List.of(member));

        assertDoesNotThrow(() -> adminDashboardService.deleteUserByAdmin(10, 1));

        verify(clubMemberRepository).deleteAll(List.of(member));
        verify(userRepository).delete(sampleUser);
    }

    @Test
    @DisplayName("Admin không thể tự xóa tài khoản của chính mình")
    void deleteUserByAdmin_SelfDelete_ThrowsException() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminDashboardService.deleteUserByAdmin(1, 1));
        assertTrue(ex.getMessage().contains("tự xóa tài khoản của chính mình"));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Admin xóa tài khoản không tồn tại ném NoSuchElementException")
    void deleteUserByAdmin_UserNotFound_ThrowsException() {
        when(userRepository.findById(999)).thenReturn(Optional.empty());

        assertThrows(NoSuchElementException.class,
                () -> adminDashboardService.deleteUserByAdmin(999, 1));
        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("Không cho phép xóa nếu user đang làm Chủ nhiệm CLB đang hoạt động")
    void deleteUserByAdmin_UserIsPresident_ThrowsException() {
        when(userRepository.findById(10)).thenReturn(Optional.of(sampleUser));
        ClubMember presidentMember = ClubMember.builder()
                .role(ClubMemberRole.PRESIDENT)
                .status(ClubMemberStatus.ACTIVE)
                .build();
        when(clubMemberRepository.findByUserUserId(10)).thenReturn(List.of(presidentMember));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> adminDashboardService.deleteUserByAdmin(10, 1));
        assertTrue(ex.getMessage().contains("đang là Chủ nhiệm"));
        verify(userRepository, never()).delete(any());
    }
}

