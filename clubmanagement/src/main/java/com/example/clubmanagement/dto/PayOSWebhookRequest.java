package com.example.clubmanagement.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.payos.type.Webhook;
import vn.payos.type.WebhookData;

/**
 * vn.payos.type.Webhook khong co default constructor nen Jackson 3 (Spring Boot 4)
 * kh the binder duoc -> IPN that bi 500 truong khi kiem chu ky.
 * DTO nay doc JSON cua PayOS roi dung lai Webhook cua SDK bang builder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PayOSWebhookRequest {

    private String code;
    private String desc;
    private Boolean success;
    private String signature;
    private OrderData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class OrderData {
        private Long orderCode;
        private Integer amount;
        private String description;
        private String accountNumber;
        private String reference;
        private String transactionDateTime;
        private String currency;
        private String paymentLinkId;
        private String code;
        private String desc;
        private String counterAccountBankId;
        private String counterAccountBankName;
        private String counterAccountName;
        private String counterAccountNumber;
        private String virtualAccountName;
        private String virtualAccountNumber;
    }

    /**
     * tra ve null khi day chi la Ping kiem tra URL cua PayOS (khong co noi dung don hang).
     */
    public Webhook toWebhook() {
        if (data == null || data.getOrderCode() == null || signature == null || signature.isBlank()) {
            return null;
        }
        WebhookData webhookData = WebhookData.builder()
                .orderCode(data.getOrderCode())
                .amount(data.getAmount() == null ? 0 : data.getAmount())
                .description(nz(data.getDescription()))
                .accountNumber(nz(data.getAccountNumber()))
                .reference(nz(data.getReference()))
                .transactionDateTime(nz(data.getTransactionDateTime()))
                .currency(nz(data.getCurrency()))
                .paymentLinkId(nz(data.getPaymentLinkId()))
                .code(nz(data.getCode()))
                .desc(nz(data.getDesc()))
                .counterAccountBankId(data.getCounterAccountBankId())
                .counterAccountBankName(data.getCounterAccountBankName())
                .counterAccountName(data.getCounterAccountName())
                .counterAccountNumber(data.getCounterAccountNumber())
                .virtualAccountName(data.getVirtualAccountName())
                .virtualAccountNumber(data.getVirtualAccountNumber())
                .build();
        return Webhook.builder()
                .code(nz(code))
                .desc(nz(desc))
                .success(success)
                .data(webhookData)
                .signature(signature)
                .build();
    }

    /** WebhookData cua SDK danh dau @NonNull cho cac field nay nen khong duoc truyen null. */
    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
