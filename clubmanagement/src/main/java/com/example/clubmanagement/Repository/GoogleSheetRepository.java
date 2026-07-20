package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.GoogleSheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleSheetRepository extends JpaRepository<GoogleSheet, Integer> {
    List<GoogleSheet> findByUserUserId(Integer userId);
    Optional<GoogleSheet> findBySpreadsheetIdAndUserUserId(String spreadsheetId, Integer userId);

    // ─── Phân quyền theo CLB ───
    List<GoogleSheet> findByClubId(Integer clubId);
    Optional<GoogleSheet> findBySpreadsheetIdAndClubId(String spreadsheetId, Integer clubId);
}
