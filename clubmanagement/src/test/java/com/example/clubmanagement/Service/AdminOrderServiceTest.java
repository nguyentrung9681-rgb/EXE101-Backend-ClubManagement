package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.PaymentOrder;
import com.example.clubmanagement.Entity.SubscriptionPackage;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Repository.PaymentOrderRepository;
import com.example.clubmanagement.dto.AdminOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class AdminOrderServiceTest {

    @Mock
    private PaymentOrderRepository orderRepository;

    @InjectMocks
    private AdminOrderService adminOrderService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private PaymentOrder order(String transactionNo) {
        User buyer = User.builder().userId(7).fullName("Nguyen A").email("a@example.com").phoneNumber("0900").build();
        Club club = new Club();
        club.setId(3);
        club.setName("S-Club Demo");
        SubscriptionPackage pack = new SubscriptionPackage();
        pack.setId(2);
        pack.setCode("MONTHLY");
        pack.setName("Gói tháng");
        return PaymentOrder.builder()
                .id(100).orderCode(900123L).amount(new BigDecimal("99000")).status(OrderStatus.PAID)
                .paymentMethod("VIETQR").transactionNo(transactionNo)
                .user(buyer).club(club).subscriptionPackage(pack)
                .build();
    }

    @SuppressWarnings("unchecked")
    private void stubPage(PaymentOrder order) {
        Page<PaymentOrder> page = new PageImpl<>(List.of(order));
        when(orderRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
    }

    @Test
    @DisplayName("Đơn có mã tham chiếu cổng -> referenceSource GATEWAY, kèm thông tin khách để spot-check")
    void mapsGatewayReference() {
        stubPage(order("FT2611090001"));

        AdminOrderResponse dto = adminOrderService.getOrders("PAID", null, null, null, 0, 20).getContent().get(0);

        assertEquals(900123L, dto.getOrderCode());
        assertEquals("GATEWAY", dto.getReferenceSource());
        assertEquals("FT2611090001", dto.getTransactionNo());
        assertEquals("a@example.com", dto.getCustomerEmail());
        assertEquals("0900", dto.getCustomerPhone());
        assertEquals("S-Club Demo", dto.getClubName());
        assertEquals("MONTHLY", dto.getPackageCode());
    }

    @Test
    @DisplayName("Đơn xác nhận tay MANUAL_ -> referenceSource MANUAL (không đối chiếu sao kê được)")
    void marksManualReference() {
        stubPage(order("MANUAL_1731139200"));
        AdminOrderResponse dto = adminOrderService.getOrders(null, null, null, null, 0, 20).getContent().get(0);
        assertEquals("MANUAL", dto.getReferenceSource());
    }

    @Test
    @DisplayName("Đơn chưa có mã tham chiếu -> referenceSource NONE")
    void marksNoReference() {
        stubPage(order(null));
        AdminOrderResponse dto = adminOrderService.getOrders(null, null, null, null, 0, 20).getContent().get(0);
        assertEquals("NONE", dto.getReferenceSource());
    }

    @Test
    @DisplayName("Status không hợp lệ ném IllegalArgumentException")
    void invalidStatusThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminOrderService.getOrders("REFUNDED", null, null, null, 0, 20));
    }

    @Test
    @DisplayName("from sau to ném IllegalArgumentException")
    void invalidDateRangeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> adminOrderService.getOrders(null, null, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 1), 0, 20));
    }

    @Test
    @DisplayName("Không lọc status thì gọi repository với specification")
    void nullStatusPassedThrough() {
        stubPage(order("FT1"));
        adminOrderService.getOrders("  ", null, null, null, 0, 20);
        org.mockito.Mockito.verify(orderRepository)
                .findAll(any(Specification.class), any(Pageable.class));
    }
}