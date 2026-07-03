package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.Club;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {
    //Tìm kiếm clb theo từ khóa (tên hoặc mô tả)
    List<Club> findByClubNameContainingOrDescriptionContaining(String nameKey, String descKey);
}
