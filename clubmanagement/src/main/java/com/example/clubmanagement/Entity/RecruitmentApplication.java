package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.ApplicationStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "recruitment_applications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecruitmentApplication {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(length = 1000)
    private String applicationContent; //ly do muon vao clb/ form thông tin

    @Enumerated(EnumType.STRING)
    private ApplicationStatus status; //PENDING, APPROVED, REJECTED

    private String rejectReason; //Lưu câu thông báo từ chối mặc định
    private LocalDateTime createdAt;
}
