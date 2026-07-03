package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Entity.ClubPost;
import com.example.clubmanagement.Enum.PostStatus;
import com.example.clubmanagement.Enum.Visibility;
import com.example.clubmanagement.Repository.ClubPostRepository;
import com.example.clubmanagement.Repository.ClubRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ClubService {
    @Autowired
    private ClubRepository clubRepository;
    @Autowired
    private ClubPostRepository clubPostRepository;

    public List<ClubPost> getPublicPosts() {
        return clubPostRepository.findByVisibilityAndPostStatus(Visibility.PUBLIC, PostStatus.PUBLISHED);
    }

    public List<Club> searchClubs(String keyword) {
        return clubRepository.findByClubNameContainingOrDescriptionContaining(keyword, keyword);
    }
}
