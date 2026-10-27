package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Club;
import com.example.clubmanagement.Enum.ClubStatus;
import com.example.clubmanagement.Enum.ClubVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClubRepository extends JpaRepository<Club, Integer> {
    long countByStatus(ClubStatus status);
    long countByVisibility(ClubVisibility visibility);
}
