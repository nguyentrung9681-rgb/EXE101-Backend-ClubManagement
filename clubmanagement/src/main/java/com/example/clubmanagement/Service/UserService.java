package com.example.clubmanagement.Service;

import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.dto.ChangePasswordRequest;
import com.example.clubmanagement.dto.UserProfileRequest;
import com.example.clubmanagement.dto.UserProfileResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfileResponse getUserProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));
        return mapToUserProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateUserProfile(Integer userId, UserProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        // Nếu là tài khoản Google, không cho phép đổi email và username
        if ("GOOGLE".equals(user.getAuthProvider())) {
            if ((request.getEmail() != null && !user.getEmail().equalsIgnoreCase(request.getEmail())) || 
                (request.getUsername() != null && !user.getUsername().equalsIgnoreCase(request.getUsername()))) {
                throw new RuntimeException("Tài khoản đăng nhập qua Google không được phép thay đổi tên đăng nhập hoặc email!");
            }
        }

        // Kiểm tra trùng username với các tài khoản khác
        if (request.getUsername() != null && !request.getUsername().equalsIgnoreCase(user.getUsername())) {
            userRepository.findByUsername(request.getUsername()).ifPresent(existing -> {
                if (!existing.getUserId().equals(userId)) {
                    throw new RuntimeException("Tên tài khoản đã tồn tại!");
                }
            });
            user.setUsername(request.getUsername());
        }

        // Kiểm tra trùng email với các tài khoản khác
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
                if (!existing.getUserId().equals(userId)) {
                    throw new RuntimeException("Email đã được sử dụng!");
                }
            });
            user.setEmail(request.getEmail());
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }

        if (request.getPhoneNumber() != null) {
            String phone = request.getPhoneNumber().trim();
            if (!phone.isEmpty() && !phone.matches("^\\d{10}$")) {
                throw new RuntimeException("Số điện thoại phải có đúng 10 chữ số!");
            }
            user.setPhoneNumber(phone.isEmpty() ? null : phone);
        }

        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User updatedUser = userRepository.save(user);
        return mapToUserProfileResponse(updatedUser);
    }

    @Transactional
    public void changePassword(Integer userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng!"));

        if ("GOOGLE".equals(user.getAuthProvider())) {
            throw new RuntimeException("Tài khoản đăng nhập qua Google không thể thay đổi mật khẩu!");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Mật khẩu cũ không chính xác!");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không khớp!");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private UserProfileResponse mapToUserProfileResponse(User user) {
        return UserProfileResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .avatarUrl(user.getAvatarUrl())
                .authProvider(user.getAuthProvider())
                .userStatus(user.getUserStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
