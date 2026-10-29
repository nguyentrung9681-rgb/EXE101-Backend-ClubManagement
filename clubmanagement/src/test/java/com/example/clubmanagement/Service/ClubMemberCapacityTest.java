package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubMember;
import com.example.clubmanagement.Entity.ClubSubscription;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.ClubVisibility;
import com.example.clubmanagement.Enum.SubscriptionStatus;
import com.example.clubmanagement.Repository.ClubMemberRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import com.example.clubmanagement.Repository.ClubSubscriptionRepository;
import com.example.clubmanagement.Repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ClubMemberCapacityTest {

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubSubscriptionRepository clubSubscriptionRepository;

    @InjectMocks
    private ClubService clubService;

    private Club publicClub;
    private Club privateClub;
    private User testUser;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        publicClub = Club.builder()
                .id(1)
                .name("Public Test Club")
                .visibility(ClubVisibility.PUBLIC)
                .build();

        privateClub = Club.builder()
                .id(2)
                .name("Private Test Club")
                .visibility(ClubVisibility.PRIVATE)
                .build();

        testUser = User.builder()
                .userId(100)
                .fullName("Nguyen Van A")
                .build();

        when(userRepository.findById(100)).thenReturn(Optional.of(testUser));
    }

    @Test
    @DisplayName("CLB Public chưa có gói nâng cấp nhưng dưới 30 thành viên: tham gia thành công")
    void joinPublicClub_Under30Members_Success() {
        when(clubRepository.findById(1)).thenReturn(Optional.of(publicClub));
        when(clubMemberRepository.findByClubIdAndUserUserId(1, 100)).thenReturn(Optional.empty());
        when(clubMemberRepository.countByClubIdAndStatus(1, ClubMemberStatus.ACTIVE)).thenReturn(25L);
        when(clubMemberRepository.save(any(ClubMember.class))).thenAnswer(inv -> inv.getArgument(0));

        ClubMember member = clubService.joinClub(1, 100);

        assertNotNull(member);
        assertEquals(ClubMemberStatus.ACTIVE, member.getStatus());
        verify(clubMemberRepository).save(any(ClubMember.class));
    }

    @Test
    @DisplayName("CLB Public chưa có gói nâng cấp và đã đủ 30 thành viên: chặn thành viên thứ 31")
    void joinPublicClub_Reached30Members_NoSubscription_ThrowsException() {
        when(clubRepository.findById(1)).thenReturn(Optional.of(publicClub));
        when(clubMemberRepository.findByClubIdAndUserUserId(1, 100)).thenReturn(Optional.empty());
        when(clubMemberRepository.countByClubIdAndStatus(1, ClubMemberStatus.ACTIVE)).thenReturn(30L);
        when(clubSubscriptionRepository.findFirstByClubIdAndStatusOrderByEndDateDesc(1, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> clubService.joinClub(1, 100));
        assertTrue(ex.getMessage().contains("đạt giới hạn tối đa 30"));
        verify(clubMemberRepository, never()).save(any(ClubMember.class));
    }

    @Test
    @DisplayName("CLB Public đã đủ 30 thành viên nhưng có gói Active còn hạn: cho phép thành viên thứ 31 tham gia")
    void joinPublicClub_Reached30Members_WithActiveSubscription_Success() {
        when(clubRepository.findById(1)).thenReturn(Optional.of(publicClub));
        when(clubMemberRepository.findByClubIdAndUserUserId(1, 100)).thenReturn(Optional.empty());
        when(clubMemberRepository.countByClubIdAndStatus(1, ClubMemberStatus.ACTIVE)).thenReturn(30L);

        ClubSubscription activeSub = ClubSubscription.builder()
                .club(publicClub)
                .status(SubscriptionStatus.ACTIVE)
                .endDate(LocalDateTime.now().plusDays(30))
                .build();
        when(clubSubscriptionRepository.findFirstByClubIdAndStatusOrderByEndDateDesc(1, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSub));
        when(clubMemberRepository.save(any(ClubMember.class))).thenAnswer(inv -> inv.getArgument(0));

        ClubMember member = clubService.joinClub(1, 100);

        assertNotNull(member);
        assertEquals(ClubMemberStatus.ACTIVE, member.getStatus());
        verify(clubMemberRepository).save(any(ClubMember.class));
    }

    @Test
    @DisplayName("Chủ nhiệm duyệt thành viên thứ 31 vào CLB khi chưa nâng cấp gói: bị chặn")
    void approveMember_Reached30Members_NoSubscription_ThrowsException() {
        when(clubMemberRepository.findByClubIdAndUserUserId(1, 999))
                .thenReturn(Optional.of(ClubMember.builder().role(ClubMemberRole.PRESIDENT).status(ClubMemberStatus.ACTIVE).build()));

        ClubMember pendingMember = ClubMember.builder()
                .id(200)
                .club(publicClub)
                .user(testUser)
                .status(ClubMemberStatus.PENDING)
                .build();

        when(clubMemberRepository.findById(200)).thenReturn(Optional.of(pendingMember));
        when(clubMemberRepository.countByClubIdAndStatus(1, ClubMemberStatus.ACTIVE)).thenReturn(30L);
        when(clubSubscriptionRepository.findFirstByClubIdAndStatusOrderByEndDateDesc(1, SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> clubService.approveMember(1, 200, 999, true));
        assertTrue(ex.getMessage().contains("đạt giới hạn tối đa 30"));
        assertEquals(ClubMemberStatus.PENDING, pendingMember.getStatus());
    }
}
