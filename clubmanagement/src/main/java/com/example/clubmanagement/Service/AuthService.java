package com.example.clubmanagement.Service;

import com.example.clubmanagement.dto.*;
import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.Config.JwtTokenProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    public AuthService(UserRepository userRepository, UserSettingRepository userSettingRepository,
                       PasswordEncoder passwordEncoder, JwtTokenProvider tokenProvider) {
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
    }

    @Transactional
    public String registerLocal(RegisterRequest request) {
        // Kiểm tra mật khẩu khớp nhau
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không trùng khớp!");
        }

        // Kiểm tra trùng lặp tài khoản/email
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new RuntimeException("Tên tài khoản đã tồn tại!");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email đã được sử dụng!");
        }

        // Tạo người dùng mới
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .fullName(request.getFullName() != null ? request.getFullName() : request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .authProvider("LOCAL")
                .userStatus("ACTIVE")
                .build();

        User savedUser = userRepository.save(user);

        // Tạo thiết lập mặc định (UserSettings) cho User mới
        UserSetting setting = new UserSetting();
        setting.setUserId(savedUser.getUserId());
        userSettingRepository.save(setting);

        return "Đăng ký thành công!";
    }

    public AuthResponse loginLocal(LoginRequest request) {
        // FIX: Tìm qua Username trước, nếu không thấy thì tìm qua Email (hoặc ngược lại)
        String loginInput = request.getEmailOrUsername().trim();

        User user = userRepository.findByUsernameOrEmail(loginInput, loginInput)
                .orElseThrow(() -> new RuntimeException("Tài khoản hoặc email không tồn tại!"));

        if ("BANNED".equals(user.getUserStatus()) || "INACTIVE".equals(user.getUserStatus())) {
            throw new RuntimeException("Tài khoản này đã bị khóa hoặc không hoạt động!");
        }

        if (!"LOCAL".equals(user.getAuthProvider())) {
            throw new RuntimeException("Tài khoản này đăng ký qua Google. Vui lòng đăng nhập bằng Google!");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Mật khẩu không chính xác!");
        }
        //Ktra lần cuối user login role nào
        UserSetting setting = userSettingRepository.findById(user.getUserId()).orElse(null);
        Integer lastClubId = (setting != null) ? setting.getLastSelectedClubId() : null;

        String token = tokenProvider.generateToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .authProvider(user.getAuthProvider())
                .lastSelectedClubId(lastClubId)
                .build();
    }

    @Transactional
    public AuthResponse processGoogleUser(String email, String name, String googleId, String avatarUrl) {
        // FIX: Khai báo biến user ở phạm vi hàm để tránh lỗi mất scope nhận diện
        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);

        if (user != null && ("BANNED".equals(user.getUserStatus()) || "INACTIVE".equals(user.getUserStatus()))) {
            throw new RuntimeException("Tài khoản này đã bị khóa hoặc không hoạt động!");
        }

        if (user == null) {
            // Case 2: Đăng ký tự động bằng Google nếu chưa có tài khoản
            String baseUsername = email.split("@")[0];
            String username = baseUsername;
            int count = 1;
            while (userRepository.existsByUsername(username)) {
                username = baseUsername + count;
                count++;
            }

            user = User.builder()
                    .username(username)
                    .email(email)
                    .fullName(name)
                    .googleId(googleId)
                    .authProvider("GOOGLE")
                    .passwordHash(null)
                    .avatarUrl(avatarUrl)
                    .userStatus("ACTIVE")
                    .build();

            user = userRepository.save(user);

            // Khởi tạo cài đặt mặc định
            UserSetting setting = new UserSetting();
            setting.setUserId(user.getUserId());
            userSettingRepository.save(setting);
        } else {
            // Cập nhật thông tin Google nếu người dùng đã tồn tại nhưng chưa lưu googleId/avatarUrl
            boolean updated = false;
            if (user.getGoogleId() == null && googleId != null) {
                user.setGoogleId(googleId);
                updated = true;
            }
            if (user.getAvatarUrl() == null && avatarUrl != null) {
                user.setAvatarUrl(avatarUrl);
                updated = true;
            }
            if (updated) {
                userRepository.save(user);
            }
        }

        // Đọc cấu hình điều hướng CLB
        UserSetting setting = userSettingRepository.findById(user.getUserId()).orElse(null);
        Integer lastClubId = (setting != null) ? setting.getLastSelectedClubId() : null;

        String token = tokenProvider.generateToken(user.getUsername());

        return AuthResponse.builder()
                .token(token)
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .username(user.getUsername())
                .email(user.getEmail())
                .authProvider(user.getAuthProvider())
                .lastSelectedClubId(lastClubId)
                .message("Đăng nhập/Đăng ký bằng Google thành công!")
                .build();
    }
}