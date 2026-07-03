package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.ClubPost;
import com.example.clubmanagement.Enum.PostStatus;
import com.example.clubmanagement.Enum.Visibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubPostRepository extends JpaRepository <ClubPost, Long> {
    List<ClubPost> findByVisibilityAndPostStatus(Visibility visibility, PostStatus postStatus);
}
