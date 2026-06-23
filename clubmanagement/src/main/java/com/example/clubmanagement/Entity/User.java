package com.example.clubmanagement.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "Users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userId;

    private String fullName;
    private String username;
    private String email;
    private String passwordHash;

    private String authProvider; // 'LOCAL' hoặc 'GOOGLE'
    private String googleId;
    private String avatarUrl;
    private String phoneNumber;
    private String studentCode;

    private String userStatus; // 'ACTIVE', 'INACTIVE', 'BANNED'

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (userStatus == null) userStatus = "ACTIVE";
        if (authProvider == null) authProvider = "LOCAL";
    }
}
