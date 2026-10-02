package com.example.clubmanagement.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPackageResponse {
    private Integer id;
    private String code;
    private String name;
    private BigDecimal price;
    private Integer durationDays;
    private String description;
    private String features;
}
