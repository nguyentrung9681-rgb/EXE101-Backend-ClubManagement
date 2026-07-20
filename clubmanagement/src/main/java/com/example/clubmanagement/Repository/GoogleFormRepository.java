package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.GoogleForm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleFormRepository extends JpaRepository<GoogleForm, Integer> {
    List<GoogleForm> findByUserUserId(Integer userId);
    Optional<GoogleForm> findByFormIdAndUserUserId(String formId, Integer userId);

    // ─── Phân quyền theo CLB ───
    List<GoogleForm> findByClubId(Integer clubId);
    Optional<GoogleForm> findByFormIdAndClubId(String formId, Integer clubId);
}
