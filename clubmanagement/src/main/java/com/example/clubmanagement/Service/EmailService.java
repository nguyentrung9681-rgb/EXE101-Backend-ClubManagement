package com.example.clubmanagement.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Autowired(required = false)
    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetUrl, String token) {
        // Luôn in mã OTP ra Terminal Console log để hỗ trợ test nhanh khi chưa có SMTP
        log.info("\n==================================================\n[OTP RESET PASSWORD]\nEmail nhận: {}\nMã OTP 6 số: {}\nLink Reset: {}\n==================================================", toEmail, token, resetUrl);

        String subject = "[S-Club] Yêu cầu đặt lại mật khẩu của bạn";
        String htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px; background-color: #ffffff;">
                <h2 style="color: #4F46E5; text-align: center;">Hệ thống Quản lý CLB S-Club</h2>
                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                <p>Xin chào <strong>%s</strong>,</p>
                <p>Bạn đã gửi yêu cầu đặt lại mật khẩu cho tài khoản liên kết với email <strong>%s</strong>.</p>
                <p>Mã xác thực (OTP) của bạn là: <b style="font-size: 20px; color: #4F46E5;">%s</b></p>
                <p>Vui lòng sử dụng mã OTP trên hoặc nhấn vào nút bên dưới để tiến hành đặt lại mật khẩu mới (Hiệu lực trong vòng <strong>15 phút</strong>):</p>
                
                <div style="text-align: center; margin: 30px 0;">
                    <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Đặt lại mật khẩu</a>
                </div>
                
                <p style="font-size: 13px; color: #666;">Nếu nút trên không hoạt động, bạn có thể sao chép và dán liên kết sau vào trình duyệt web:</p>
                <p style="font-size: 13px; color: #4F46E5; word-break: break-all;">%s</p>
                
                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                <p style="font-size: 12px; color: #999; text-align: center;">Nếu bạn không yêu cầu thay đổi mật khẩu, xin vui lòng bỏ qua email này hoặc liên hệ với Quản trị viên.</p>
            </div>
            """.formatted(
                recipientName != null ? recipientName : "Người dùng",
                toEmail,
                token,
                resetUrl,
                resetUrl
            );

        if (mailSender == null || mailUsername == null || mailUsername.isBlank()) {
            log.warn("Chưa cấu hình SPRING_MAIL_USERNAME trong .env. Email thực tế chưa gửi đi nhưng mã OTP đã được in ở Log trên.");
            return;
        }

        try {
            log.info("Đang tiến hành kết nối Gmail SMTP ({}) để gửi email tới {}...", mailUsername, toEmail);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(new jakarta.mail.internet.InternetAddress(mailUsername, "Hệ Thống S-Club", "UTF-8"));
            helper.setReplyTo(mailUsername);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(message);
            log.info("✅ ĐÃ GỬI EMAIL THÀNH CÔNG THÔNG QUA GMAIL SMTP TỚI: {}", toEmail);
        } catch (Exception e) {
            log.error("❌ LỖI KHI GỬI EMAIL TỚI {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
