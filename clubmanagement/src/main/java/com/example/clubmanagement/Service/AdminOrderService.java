package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.PaymentOrder;
import com.example.clubmanagement.Entity.SubscriptionPackage;
import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Enum.OrderStatus;
import com.example.clubmanagement.Repository.PaymentOrderRepository;
import com.example.clubmanagement.dto.AdminOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Locale;

/** Sổ giao dịch của Admin: liệt kê từng đơn kèm mã tham chiếu để đối chiếu sao kê. */
@Service
@Transactional(readOnly = true)
public class AdminOrderService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentOrderRepository orderRepository;

    public AdminOrderService(PaymentOrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public Page<AdminOrderResponse> getOrders(String status, String search, LocalDate from, LocalDate to,
                                              int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc!");
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        return orderRepository.searchForAdmin(
                        parseStatus(status),
                        from == null ? null : from.atStartOfDay(),
                        to == null ? null : to.plusDays(1).atStartOfDay(),
                        likePattern(search),
                        pageable)
                .map(this::toResponse);
    }

    private OrderStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Trạng thái đơn không hợp lệ! Chấp nhận: PENDING, PAID, CANCELLED, EXPIRED.");
        }
    }

    private String likePattern(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String escaped = search.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    private AdminOrderResponse toResponse(PaymentOrder order) {
        User customer = order.getUser();
        Club club = order.getClub();
        SubscriptionPackage pack = order.getSubscriptionPackage();
        String reference = order.getTransactionNo();

        return AdminOrderResponse.builder()
                .id(order.getId())
                .orderCode(order.getOrderCode())
                .amount(order.getAmount())
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .transactionNo(reference)
                .referenceSource(referenceSource(reference))
                .userId(customer == null ? null : customer.getUserId())
                .customerName(customer == null ? null : customer.getFullName())
                .customerEmail(customer == null ? null : customer.getEmail())
                .customerPhone(customer == null ? null : customer.getPhoneNumber())
                .clubId(club == null ? null : club.getId())
                .clubName(club == null ? null : club.getName())
                .packageId(pack == null ? null : pack.getId())
                .packageCode(pack == null ? null : pack.getCode())
                .packageName(pack == null ? null : pack.getName())
                .createdAt(order.getCreatedAt())
                .paidAt(order.getPaidAt())
                .cancelledAt(order.getCancelledAt())
                .build();
    }

    /** Đơn xác nhận tay không có mã tham chiếu của cổng, không thể khớp với sao kê ngân hàng. */
    private String referenceSource(String reference) {
        if (reference == null || reference.isBlank()) {
            return "NONE";
        }
        return reference.startsWith("MANUAL_") ? "MANUAL" : "GATEWAY";
    }
}