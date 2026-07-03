package com.example.clubmanagement.Entity;

import com.example.clubmanagement.Enum.PostStatus;
import com.example.clubmanagement.Enum.Visibility;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "club_posts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClubPost {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "club_id", nullable = false)
    private Club club;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String content;

    @Enumerated(EnumType.STRING)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    private PostStatus postStatus;
    private LocalDateTime createdAt;

}
