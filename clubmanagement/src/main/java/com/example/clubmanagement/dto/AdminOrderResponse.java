package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng trong sổ giao dịch của Admin, dùng để đối chiếu với sao kê PayOS / ngân hàng. */
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AdminOrderResponse {

    private Integer id;
    private Long orderCode;
    private BigDecimal amount;
    private OrderStatus status;
    private String paymentMethod;
    /** Mã tham chiếu phía cổng thanh toán trả về, khoá để khớp với sao kê. */
    private String transactionNo;
    /** GATEWAY khi có mã tham chiếu thật từ IPN, MANUAL khi xác nhận tay (không đối chiếu được). */
    private String referenceSource;

    private Integer userId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    private Integer clubId;
    private String clubName;

    private Integer packageId;
    private String packageCode;
    private String packageName;

    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
}