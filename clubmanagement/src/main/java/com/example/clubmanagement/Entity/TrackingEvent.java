package com.example.clubmanagement.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Mot hanh vi trong phin chuyen doi (view, signup, checkout, thanh toan) kem UTM first-touch. */
@Entity
@Table(name = "tracking_event", indexes = {
        @Index(name = "idx_tracking_event_name_created", columnList = "event_name, created_at"),
        @Index(name = "idx_tracking_event_created_at", columnList = "created_at"),
        @Index(name = "idx_tracking_event_utm_source", columnList = "utm_source"),
        @Index(name = "idx_tracking_event_user_id", columnList = "user_id"),
        @Index(name = "idx_tracking_event_session_id", columnList = "session_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_name", nullable = false, length = 60)
    private String eventName;

    @Column(name = "session_id", length = 64)
    private String sessionId;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "club_id")
    private Integer clubId;

    @Column(name = "order_code")
    private Long orderCode;

    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "page_path", length = 255)
    private String pagePath;

    @Column(name = "page_referrer", length = 500)
    private String pageReferrer;

    @Column(name = "utm_source", length = 80)
    private String utmSource;

    @Column(name = "utm_medium", length = 80)
    private String utmMedium;

    @Column(name = "utm_campaign", length = 120)
    private String utmCampaign;

    @Column(name = "utm_content", length = 120)
    private String utmContent;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
