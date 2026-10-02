package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Enum.ClubMemberRole;
import com.example.clubmanagement.Enum.ClubMemberStatus;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.dto.CreatePaymentRequest;
import com.example.clubmanagement.dto.PaymentOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vn.payos.PayOS;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    @Mock
    private SubscriptionPackageRepository packageRepository;

    @Mock
    private PaymentOrderRepository orderRepository;

    @Mock
    private ClubSubscriptionRepository subscriptionRepository;

    @Mock
    private ClubRepository clubRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ClubMemberRepository clubMemberRepository;

    @Mock
    private PayOS payOS;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Club clubA;
    private Club clubB;
    private SubscriptionPackage pack;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "returnUrl", "https://example.com/return");
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "cancelUrl", "https://example.com/cancel");

        user = User.builder().userId(1).fullName("Nguyễn Văn A").email("a@gmail.com").build();
        clubA = Club.builder().id(10).name("Câu lạc bộ Âm nhạc (CLB A)").build();
        clubB = Club.builder().id(20).name("Câu lạc bộ Thể thao (CLB B)").build();

        pack = SubscriptionPackage.builder()
                .id(1)
                .code("BASIC_MONTHLY")
                .name("Gói Cơ Bản 1 Tháng")
                .price(new BigDecimal("199000"))
                .durationDays(30)
                .build();
    }

    @Test
    @DisplayName("Tạo đơn hàng thành công khi người dùng là CHỦ NHIỆM (PRESIDENT) của CLB")
    void testCreateCheckoutOrder_Success_WhenUserIsPresident() {
        // Mock dữ liệu: User 1 là PRESIDENT của Club B (20)
        ClubMember presidentMember = ClubMember.builder()
                .id(100)
                .club(clubB)
                .user(user)
                .role(ClubMemberRole.PRESIDENT)
                .status(ClubMemberStatus.ACTIVE)
                .build();

        when(clubRepository.findById(20)).thenReturn(Optional.of(clubB));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(clubMemberRepository.findByClubIdAndUserUserIdAndStatus(20, 1, ClubMemberStatus.ACTIVE))
                .thenReturn(Optional.of(presidentMember));
        when(packageRepository.findById(1)).thenReturn(Optional.of(pack));
        when(orderRepository.save(any(PaymentOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .clubId(20)
                .userId(1)
                .packageId(1)
                .build();

        PaymentOrderResponse response = paymentService.createCheckoutOrder(request);

        assertNotNull(response);
        assertEquals(20, response.getClubId());
        assertEquals(1, response.getUserId());
        assertEquals(OrderStatus.PENDING, response.getStatus());
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại (Ném lỗi) khi người dùng chỉ là THÀNH VIÊN (MEMBER) của CLB")
    void testCreateCheckoutOrder_ThrowsException_WhenUserIsMember() {
        // Mock dữ liệu: User 1 chỉ là MEMBER của Club A (10)
        ClubMember regularMember = ClubMember.builder()
                .id(101)
                .club(clubA)
                .user(user)
                .role(ClubMemberRole.MEMBER)
                .status(ClubMemberStatus.ACTIVE)
                .build();

        when(clubRepository.findById(10)).thenReturn(Optional.of(clubA));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(clubMemberRepository.findByClubIdAndUserUserIdAndStatus(10, 1, ClubMemberStatus.ACTIVE))
                .thenReturn(Optional.of(regularMember));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .clubId(10)
                .userId(1)
                .packageId(1)
                .build();

        Exception exception = assertThrows(RuntimeException.class, () -> paymentService.createCheckoutOrder(request));
        assertTrue(exception.getMessage().contains("Chỉ Chủ nhiệm câu lạc bộ (PRESIDENT) mới có quyền"));
    }

    @Test
    @DisplayName("Tạo đơn hàng thất bại khi người dùng không thuộc về CLB")
    void testCreateCheckoutOrder_ThrowsException_WhenUserNotInClub() {
        when(clubRepository.findById(30)).thenReturn(Optional.of(Club.builder().id(30).name("CLB C").build()));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(clubMemberRepository.findByClubIdAndUserUserIdAndStatus(30, 1, ClubMemberStatus.ACTIVE))
                .thenReturn(Optional.empty());

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .clubId(30)
                .userId(1)
                .packageId(1)
                .build();

        Exception exception = assertThrows(RuntimeException.class, () -> paymentService.createCheckoutOrder(request));
        assertTrue(exception.getMessage().contains("không phải là thành viên đang hoạt động"));
    }

    @Test
    @DisplayName("Kểm tra canUserPayForClub trả về true với PRESIDENT và false với MEMBER")
    void testCanUserPayForClub() {
        ClubMember president = ClubMember.builder().role(ClubMemberRole.PRESIDENT).status(ClubMemberStatus.ACTIVE).build();
        ClubMember member = ClubMember.builder().role(ClubMemberRole.MEMBER).status(ClubMemberStatus.ACTIVE).build();

        when(clubMemberRepository.findByClubIdAndUserUserIdAndStatus(20, 1, ClubMemberStatus.ACTIVE)).thenReturn(Optional.of(president));
        when(clubMemberRepository.findByClubIdAndUserUserIdAndStatus(10, 1, ClubMemberStatus.ACTIVE)).thenReturn(Optional.of(member));

        assertTrue(paymentService.canUserPayForClub(20, 1), "User là Chủ nhiệm CLB B (20) -> Phải trả về true");
        assertFalse(paymentService.canUserPayForClub(10, 1), "User là Thành viên CLB A (10) -> Phải trả về false");
        assertFalse(paymentService.canUserPayForClub(30, 1), "User không ở trong CLB C (30) -> Phải trả về false");
    }
}
