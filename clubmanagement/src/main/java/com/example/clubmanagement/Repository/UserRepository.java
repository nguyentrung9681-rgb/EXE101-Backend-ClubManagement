package com.example.clubmanagement.Repository;

import com.example.clubmanagement.Entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Integer> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByUsernameOrEmail(String username, String email);

    long countByUserStatus(String userStatus);

    long countByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
}