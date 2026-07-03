package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.RecruitmentApplication;
import com.example.clubmanagement.Enum.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecruitmentApplicationRepository extends JpaRepository<RecruitmentApplication, Long> {
    //tìm đơn đặng kí theo club và trạng thái
    List<RecruitmentApplication> findByClubIdAndStatus(Long clubId, ApplicationStatus status);

    //check xem user đã gửi đơn cho clb này chưa và đang đợi duyệt
    boolean existsByUserUserIdAndClubIdAndStatus(Integer userId, Long clubId, ApplicationStatus status);
}
