package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.GoogleAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface GoogleAccountRepository extends JpaRepository<GoogleAccount, Integer> {
    List<GoogleAccount> findByUserUserId(Integer userId);
    Optional<GoogleAccount> findFirstByUserUserIdOrderByCreatedAtDesc(Integer userId);
    Optional<GoogleAccount> findByUserUserIdAndGoogleEmail(Integer userId, String googleEmail);
}
