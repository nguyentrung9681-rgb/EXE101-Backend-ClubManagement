package com.example.clubmanagement.Service;

import com.example.clubmanagement.dto.*;
import com.example.clubmanagement.Entity.*;
import com.example.clubmanagement.Repository.*;
import com.example.clubmanagement.Config.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;

    @Value("${app.frontend.redirect-url:https://exe-ebon.vercel.app}")
    private String frontendUrl;

    public AuthService(UserRepository userRepository,
                       UserSettingRepository userSettingRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailService emailService) {
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailService = emailService;
    }

    @Transactional
    public String registerLocal(RegisterRequest request) {
        if (request.getPassword() == null || request.getPassword().length() < 8) {
            throw new RuntimeException("Mật khẩu phải chứa ít nhất 8 ký tự!");
        }

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
        User user = userRepository.findByGoogleId(googleId)
                .or(() -> userRepository.findByEmail(email))
                .orElse(null);

        if (user != null && ("BANNED".equals(user.getUserStatus()) || "INACTIVE".equals(user.getUserStatus()))) {
            throw new RuntimeException("Tài khoản này đã bị khóa hoặc không hoạt động!");
        }

        if (user == null) {
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

            UserSetting setting = new UserSetting();
            setting.setUserId(user.getUserId());
            userSettingRepository.save(setting);
        } else {
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

    @Transactional
    public String forgotPassword(ForgotPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Email hoặc tên tài khoản không được để trống!");
        }

        String identifier = request.getEmail().trim();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản liên kết với thông tin này!"));

        if (!"LOCAL".equals(user.getAuthProvider())) {
            throw new RuntimeException("Tài khoản này đăng ký qua Google. Vui lòng sử dụng Đăng nhập bằng Google!");
        }

        if ("BANNED".equals(user.getUserStatus()) || "INACTIVE".equals(user.getUserStatus())) {
            throw new RuntimeException("Tài khoản này đã bị khóa hoặc không hoạt động!");
        }

        // Xóa token cũ nếu có và áp dụng ngay xuống CSDL
        passwordResetTokenRepository.findByUser(user).ifPresent(oldToken -> {
            passwordResetTokenRepository.delete(oldToken);
            passwordResetTokenRepository.flush();
        });

        // Tạo mã OTP 6 số ngẫu nhiên an toàn (SecureRandom) & Token UUID
        String otp = String.format("%06d", new SecureRandom().nextInt(900000) + 100000);
        String token = UUID.randomUUID().toString();

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .token(token)
                .otp(otp)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(15))
                .build();

        passwordResetTokenRepository.save(resetToken);

        // Tạo liên kết reset password cho email
        String resetUrl = frontendUrl + "/reset-password?token=" + token;

        // Gửi email xác thực bất đồng bộ
        emailService.sendPasswordResetEmail(user.getEmail(), user.getFullName(), resetUrl, otp);

        return "Mã xác thực (OTP) đặt lại mật khẩu đã được gửi đến email " + user.getEmail() + ". Vui lòng kiểm tra hộp thư!";
    }

    @Transactional
    public String resetPassword(ResetPasswordRequest request) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Email hoặc tên tài khoản không được để trống!");
        }
        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            throw new RuntimeException("Mật khẩu mới phải chứa ít nhất 8 ký tự!");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("Mật khẩu xác nhận không trùng khớp!");
        }

        if (request.getOtp() == null || request.getOtp().isBlank()) {
            throw new RuntimeException("Vui lòng cung cấp Mã xác thực (OTP)!");
        }
        String otp = request.getOtp().trim();

        String identifier = request.getEmail().trim();
        User user = userRepository.findByEmail(identifier)
                .or(() -> userRepository.findByUsername(identifier))
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản liên kết với Email/Tên đăng nhập này!"));

        if (!"LOCAL".equals(user.getAuthProvider())) {
            throw new RuntimeException("Tài khoản này đăng ký qua Google. Không thể đặt lại mật khẩu theo cách này!");
        }

        if ("BANNED".equals(user.getUserStatus()) || "INACTIVE".equals(user.getUserStatus())) {
            throw new RuntimeException("Tài khoản này đã bị khóa hoặc không hoạt động!");
        }

        // Tìm token theo OTP/User
        PasswordResetToken resetToken = passwordResetTokenRepository.findByOtpAndUser(otp, user)
                .orElseThrow(() -> new RuntimeException("Mã OTP xác thực không chính xác!"));

        if (resetToken.isExpired()) {
            passwordResetTokenRepository.delete(resetToken);
            throw new RuntimeException("Mã xác thực đã hết hạn (chỉ có hiệu lực trong 15 phút). Vui lòng gửi lại yêu cầu!");
        }

        // Cập nhật mật khẩu mới
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        // Xóa token sau khi đổi mật khẩu thành công
        passwordResetTokenRepository.delete(resetToken);

        return "Đặt lại mật khẩu thành công! Vui lòng đăng nhập lại với mật khẩu mới.";
    }
}