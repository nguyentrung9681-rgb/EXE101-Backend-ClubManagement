package com.example.clubmanagement.Config;

import com.example.clubmanagement.Entity.User;
import com.example.clubmanagement.Entity.UserSetting;
import com.example.clubmanagement.Enum.SystemRole;
import com.example.clubmanagement.Repository.UserRepository;
import com.example.clubmanagement.Repository.UserSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final JdbcTemplate jdbcTemplate;
    private final UserRepository userRepository;
    private final UserSettingRepository userSettingRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.default-username:admin}")
    private String defaultAdminUsername;

    @Value("${app.admin.default-password:Admin@123456}")
    private String defaultAdminPassword;

    @Value("${app.admin.default-email:admin@sclub.com}")
    private String defaultAdminEmail;

    public AdminUserSeeder(
            JdbcTemplate jdbcTemplate,
            UserRepository userRepository,
            UserSettingRepository userSettingRepository,
            PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.userRepository = userRepository;
        this.userSettingRepository = userSettingRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        try {
            // 1. Đảm bảo cột role tồn tại trong public.users trước khi thực hiện bất kỳ câu truy vấn JPA nào
            // (Đặc biệt trên Supabase có bảng auth.users cũng có cột role, nên cần chỉ định rõ ràng public.users)
            try {
                jdbcTemplate.execute("ALTER TABLE public.users ADD COLUMN IF NOT EXISTS role VARCHAR(20) DEFAULT 'USER' NOT NULL;");
                jdbcTemplate.execute("UPDATE public.users SET role = 'USER' WHERE role IS NULL;");
            } catch (Exception ddlEx) {
                log.warn("Không thể chạy DDL ALTER TABLE users (có thể đã tồn tại): {}", ddlEx.getMessage());
            }

            // 2. Kiểm tra xem hệ thống đã có tài khoản nào giữ role ADMIN chưa
            boolean hasAdmin = userRepository.existsByRole(SystemRole.ADMIN);
            if (!hasAdmin) {
                // Nếu chưa có ADMIN nào, kiểm tra xem username hoặc email mặc định đã tồn tại chưa
                User existingUser = userRepository.findByUsername(defaultAdminUsername)
                        .or(() -> userRepository.findByEmail(defaultAdminEmail))
                        .orElse(null);

                if (existingUser != null) {
                    existingUser.setRole(SystemRole.ADMIN);
                    userRepository.save(existingUser);
                    log.info("=== Đã nâng cấp tài khoản hiện có '{}' thành role ADMIN ===", existingUser.getUsername());
                } else {
                    User adminUser = User.builder()
                            .username(defaultAdminUsername)
                            .email(defaultAdminEmail)
                            .fullName("System Administrator")
                            .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                            .role(SystemRole.ADMIN)
                            .authProvider("LOCAL")
                            .userStatus("ACTIVE")
                            .build();

                    User savedAdmin = userRepository.save(adminUser);

                    UserSetting setting = new UserSetting();
                    setting.setUserId(savedAdmin.getUserId());
                    userSettingRepository.save(setting);

                    log.info("==========================================================");
                    log.info("=== KHỞI TẠO TÀI KHOẢN ADMIN MẶC ĐỊNH THÀNH CÔNG ===");
                    log.info("=== Username: {} ===", defaultAdminUsername);
                    log.info("=== Password: {} ===", defaultAdminPassword);
                    log.info("=== Email:    {} ===", defaultAdminEmail);
                    log.info("=== Role:     ADMIN ===");
                    log.info("=== LƯU Ý: Vui lòng đổi mật khẩu sau lần đăng nhập đầu tiên! ===");
                    log.info("==========================================================");
                }
            }
        } catch (Exception e) {
            log.error("Lỗi khi kiểm tra / khởi tạo tài khoản Admin mặc định: {}", e.getMessage(), e);
        }
    }
}
