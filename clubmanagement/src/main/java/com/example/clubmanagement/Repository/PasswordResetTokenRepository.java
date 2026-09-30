package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.PasswordResetToken;
import com.example.clubmanagement.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByOtpAndUser(String otp, User user);
    Optional<PasswordResetToken> findByUser(User user);
    void deleteByUser(User user);
}
