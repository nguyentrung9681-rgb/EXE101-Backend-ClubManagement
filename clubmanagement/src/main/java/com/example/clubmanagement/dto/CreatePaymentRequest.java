package com.example.clubmanagement.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {
    private Integer packageId;
    private Integer clubId;
    private Integer userId;
}
