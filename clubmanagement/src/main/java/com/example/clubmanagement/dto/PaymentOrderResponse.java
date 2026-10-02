package com.example.clubmanagement.dto;

import com.example.clubmanagement.Enum.OrderStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOrderResponse {
    private Integer id;
    private Long orderCode;
    private Integer userId;
    private Integer clubId;
    private Integer packageId;
    private String packageName;
    private BigDecimal amount;
    private OrderStatus status;
    private String checkoutUrl;
    private String qrCodeUrl;
    private String paymentMethod;
    private String transactionNo;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
