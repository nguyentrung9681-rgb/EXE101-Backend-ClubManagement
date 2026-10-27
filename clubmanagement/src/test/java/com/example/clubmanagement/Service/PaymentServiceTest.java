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
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    @Mock
    private EmailService emailService;

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
        // Dông nhu dev local: co key gia lap va duoc phep tao don hang voi QR gia.
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "clientId", "TEST_CLIENT_ID");
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "apiKey", "TEST_API_KEY");
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "checksumKey", "TEST_CHECKSUM");
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "allowUnconfiguredCheckout", true);
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "manualConfirmEnabled", false);

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

    private PaymentOrder pendingOrder() {
        return PaymentOrder.builder()
                .id(5)
                .orderCode(123L)
                .user(user)
                .club(clubB)
                .subscriptionPackage(pack)
                .amount(new BigDecimal("199000"))
                .status(OrderStatus.PENDING)
                .paymentMethod("VIETQR")
                .build();
    }

    private Webhook webhookWith(WebhookData data) {
        return Webhook.builder()
                .code("00")
                .desc("Ok")
                .success(true)
                .data(data)
                .signature("whatever")
                .build();
    }

    private WebhookData paidData(int amount, String reference) {
        return WebhookData.builder()
                .orderCode(123L)
                .amount(amount)
                .code("00")
                .desc("Ok")
                .currency("VND")
                .description("Thanh toan BASIC_MONTHLY")
                .accountNumber("0987654321")
                .reference(reference)
                .transactionDateTime("2026-10-27T10:00:00.000Z")
                .paymentLinkId("test-link-id")
                .counterAccountBankId("MB")
                .counterAccountBankName("MB Bank")
                .counterAccountName("NGUYEN VAN A")
                .counterAccountNumber("123456789")
                .virtualAccountName("NGUYEN VAN A")
                .virtualAccountNumber("123456789")
                .build();
    }

    @Test
    @DisplayName("Webhook có chữ ký không hợp lệ phải bị từ chối, không được chuyển đơn sang PAID")
    void testProcessWebhook_RejectsInvalidSignature() throws Exception {
        when(payOS.verifyPaymentWebhookData(any(Webhook.class))).thenThrow(new Exception("Data is not integrity"));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> paymentService.processWebhook(webhookWith(paidData(199000, "vFORGED"))));
        assertTrue(ex.getMessage().contains("Chữ ký webhook"));
        verify(orderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("Webhook hợp lệ nhưng số tiền không khớp đơn hàng phải bị từ chối")
    void testProcessWebhook_RejectsAmountMismatch() throws Exception {
        PaymentOrder order = pendingOrder();
        Webhook webhook = webhookWith(paidData(1, "v1"));
        when(orderRepository.findByOrderCode(123L)).thenReturn(Optional.of(order));
        when(payOS.verifyPaymentWebhookData(any(Webhook.class))).thenReturn(paidData(1, "v1"));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> paymentService.processWebhook(webhook));
        assertTrue(ex.getMessage().contains("không khớp đơn hàng"));
        assertEquals(OrderStatus.PENDING, order.getStatus());
    }

    @Test
    @DisplayName("Webhook hợp lệ và đúng số tiền: đánh dấu PAID, kích hoạt gói và gửi email xác nhận")
    void testProcessWebhook_MarksPaidAndSendsConfirmation() throws Exception {
        PaymentOrder order = pendingOrder();
        Webhook webhook = webhookWith(paidData(199000, "v251027000123"));
        when(orderRepository.findByOrderCode(123L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(PaymentOrder.class))).thenAnswer(inv -> inv.getArgument(0));
        when(payOS.verifyPaymentWebhookData(any(Webhook.class))).thenReturn(paidData(199000, "v251027000123"));

        PaymentOrderResponse response = paymentService.processWebhook(webhook);

        assertEquals(OrderStatus.PAID, response.getStatus());
        assertEquals("v251027000123", response.getTransactionNo());
        assertNotNull(order.getPaidAt());
        verify(subscriptionRepository).save(any(ClubSubscription.class));
        verify(emailService).sendOrderConfirmationEmail(
                eq("a@gmail.com"), any(), eq(123L), eq("Gói Cơ Bản 1 Tháng"),
                eq(new BigDecimal("199000")), eq("Câu lạc bộ Thể thao (CLB B)"),
                eq("VIETQR"), eq("v251027000123"), any(), any());
    }

    @Test
    @DisplayName("Endpoint xác nhận thủ công mặc định bị vô hiệu hoá")
    void testConfirmOrderManual_DisabledByDefault() {
        SecurityException ex = assertThrows(SecurityException.class,
                () -> paymentService.confirmOrderManual(123L, 1));
        assertTrue(ex.getMessage().contains("đã bị vô hiệu hoá"));
    }

    @Test
    @DisplayName("Khi bật xác nhận thủ công, người không phải chủ đơn vẫn bị từ chối")
    void testConfirmOrderManual_RejectsNonOwner() {
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "manualConfirmEnabled", true);
        when(orderRepository.findByOrderCode(123L)).thenReturn(Optional.of(pendingOrder()));

        SecurityException ex = assertThrows(SecurityException.class,
                () -> paymentService.confirmOrderManual(123L, 99));
        assertTrue(ex.getMessage().contains("không có quyền"));
    }

    @Test
    @DisplayName("Hủy đơn hàng phải kiểm tra chủ sở hữu")
    void testCancelOrder_RejectsNonOwner() {
        when(orderRepository.findByOrderCode(123L)).thenReturn(Optional.of(pendingOrder()));

        assertThrows(SecurityException.class, () -> paymentService.cancelOrder(123L, 99));
        verify(orderRepository, never()).save(any(PaymentOrder.class));
    }

    @Test
    @DisplayName("Tra trạng thái đơn hàng phải kiểm tra chủ sở hữu")
    void testGetOrderByCode_RejectsNonOwner() {
        when(orderRepository.findByOrderCode(123L)).thenReturn(Optional.of(pendingOrder()));

        assertThrows(SecurityException.class, () -> paymentService.getOrderByCode(123L, 99));
    }

    @Test
    @DisplayName("Không tạo được checkout khi PayOS chưa cấu hình và không bật chế độ dev")
    void testCreateCheckoutOrder_FailsFastWhenPayOsUnconfigured() {
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "clientId", "");
        org.springframework.test.util.ReflectionTestUtils.setField(paymentService, "allowUnconfiguredCheckout", false);

        when(clubRepository.findById(20)).thenReturn(Optional.of(clubB));
        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(clubMemberRepository.findByClubIdAndUserUserIdAndStatus(20, 1, ClubMemberStatus.ACTIVE))
                .thenReturn(Optional.of(ClubMember.builder()
                        .club(clubB).user(user).role(ClubMemberRole.PRESIDENT).status(ClubMemberStatus.ACTIVE).build()));
        when(packageRepository.findById(1)).thenReturn(Optional.of(pack));

        CreatePaymentRequest request = CreatePaymentRequest.builder()
                .clubId(20).userId(1).packageId(1).build();

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> paymentService.createCheckoutOrder(request));
        assertTrue(ex.getMessage().contains("chưa được kích hoạt"));
        verify(orderRepository, never()).save(any(PaymentOrder.class));
    }
}
