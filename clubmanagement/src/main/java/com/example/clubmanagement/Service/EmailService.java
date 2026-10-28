package com.example.clubmanagement.Service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter PAID_AT_FORMAT = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaMailSender mailSender;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${resend.api-key:}")
    private String resendApiKey;

    @Value("${resend.from-email:onboarding@resend.dev}")
    private String resendFromEmail;

    @Value("${brevo.api-key:}")
    private String brevoApiKey;

    @Value("${brevo.sender-email:Sclub.management.platform@gmail.com}")
    private String brevoSenderEmail;

    @Value("${mailjet.api-key:}")
    private String mailjetApiKey;

    @Value("${mailjet.secret-key:}")
    private String mailjetSecretKey;

    @Value("${mailjet.sender-email:Sclub.management.platform@gmail.com}")
    private String mailjetSenderEmail;

    @Autowired
    public EmailService(@Autowired(required = false) JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetUrl, String token) {
        // Luôn in mã OTP ra Terminal Console log để hỗ trợ test nhanh khi chưa có SMTP
        log.info("\n==================================================\n[OTP RESET PASSWORD]\nEmail nhận: {}\nMã OTP 6 số: {}\nLink Reset: {}\n==================================================", toEmail, token, resetUrl);

        String subject = "[S-Club] Yêu cầu đặt lại mật khẩu của bạn";
        String bodyHtml = """
            <p>Xin chào <strong>%s</strong>,</p>
            <p>Bạn đã gửi yêu cầu đặt lại mật khẩu cho tài khoản liên kết với email <strong>%s</strong>.</p>
            <p>Mã xác thực (OTP) của bạn là: <b style="font-size: 20px; color: #4F46E5;">%s</b></p>
            <p>Vui lòng sử dụng mã OTP trên hoặc nhấn vào nút bên dưới để tiến hành đặt lại mật khẩu mới (Hiệu lực trong vòng <strong>15 phút</strong>):</p>

            <div style="text-align: center; margin: 30px 0;">
                <a href="%s" style="background-color: #4F46E5; color: white; padding: 12px 24px; text-decoration: none; border-radius: 6px; font-weight: bold; display: inline-block;">Đặt lại mật khẩu</a>
            </div>

            <p style="font-size: 13px; color: #666;">Nếu nút trên không hoạt động, bạn có thể sao chép và dán liên kết sau vào trình duyệt web:</p>
            <p style="font-size: 13px; color: #4F46E5; word-break: break-all;">%s</p>

            <p style="font-size: 12px; color: #999;">Nếu bạn không yêu cầu thay đổi mật khẩu, xin vui lòng bỏ qua email này hoặc liên hệ với Quản trị viên.</p>
            """.formatted(
                recipientName != null ? recipientName : "Người dùng",
                toEmail,
                token,
                resetUrl,
                resetUrl
            );

        sendHtmlEmail(toEmail, recipientName, subject, bodyHtml);
    }

    @Async
    public void sendEmail(String toEmail, String subject, String content) {
        log.info("\n==================================================\n[EMAIL NOTIFICATION]\nEmail nhận: {}\nTiêu đề: {}\nNội dung: {}\n==================================================", toEmail, subject, content);
        sendHtmlEmail(toEmail, "Thành viên", subject, "<p style=\"font-size: 14px; color: #555; line-height: 1.6;\">" + content + "</p>");
    }

    /**
     * Hoa don xac nhan thanh toan gui toi khach hang sau khi don chuyen trang thai PAID.
     */
    @Async
    public void sendOrderConfirmationEmail(String toEmail, String recipientName, Long orderCode,
                                           String packageName, BigDecimal amount, String clubName,
                                           String paymentMethod, String transactionNo,
                                           LocalDateTime paidAt, LocalDateTime subscriptionEndDate) {
        String subject = "[S-Club] Xác nhận thanh toán thành công đơn " + orderCode;
        log.info("[EMAIL NOTIFICATION] Gửi hóa đơn xác nhận đơn {} tới {}", orderCode, toEmail);

        String bodyHtml = """
            <p>Xin chào <strong>%s</strong>,</p>
            <p>Cảm ơn bạn đã hoàn tất thanh toán trên nền tảng quản lý câu lạc bộ <strong>S-Club</strong>.
            Giao dịch của bạn đã được ghi nhận và gói dịch vụ đã được kích hoạt.</p>

            <table style="width: 100%%; border-collapse: collapse; margin: 20px 0; font-size: 14px;">
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Mã đơn hàng</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee;"><strong>%s</strong></td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Gói dịch vụ</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Câu lạc bộ</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Số tiền đã thanh toán</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee; color: #4F46E5;"><strong>%s</strong></td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Phương thức</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Mã giao dịch</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px; border-bottom: 1px solid #eee; color: #777;">Thời gian</td>
                    <td style="padding: 8px; border-bottom: 1px solid #eee;">%s</td></tr>
                <tr><td style="padding: 8px; color: #777;">Hạn sử dụng gói</td>
                    <td style="padding: 8px;">%s</td></tr>
            </table>

            <p style="font-size: 13px; color: #666;">Email này là xác nhận giao dịch của bạn trên hệ thống S-Club.
            Nếu bạn có thắc mắc về đơn hàng hoặc cần hoàn tiền, vui lòng liên hệ bộ phận hỗ trợ
            <a href="mailto:Sclub.management.platform@gmail.com" style="color: #4F46E5;">Sclub.management.platform@gmail.com</a>.</p>
            """.formatted(
                recipientName != null && !recipientName.isBlank() ? recipientName : "Quý khách",
                orderCode,
                packageName,
                clubName != null ? clubName: "-",
                formatVnd(amount),
                paymentMethod != null ? paymentMethod : "VIETQR",
                transactionNo != null ? transactionNo : "-",
                paidAt != null ? PAID_AT_FORMAT.format(paidAt) : "-",
                subscriptionEndDate != null ? DATE_FORMAT.format(subscriptionEndDate) : "Đang cập nhật");

        sendHtmlEmail(toEmail, recipientName, subject, bodyHtml);
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) return "-";
        String digits = amount.setScale(0, RoundingMode.HALF_UP).toPlainString();
        return digits.replaceAll("(\\d)(?=(\\d{3})+$)", "$1.") + " VND";
    }

    /**
     * Gom noi dung vao mau email chung cua S-Club roi gui qua nhà cung cấp đang cấu hình.
     * Cac phuong thuc cong khai o tren da co @Async nen day chi la goi noi bo.
     */
    public void sendHtmlEmail(String toEmail, String recipientName, String subject, String bodyHtml) {
        String htmlContent = """
            <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px; background-color: #ffffff;">
                <h2 style="color: #4F46E5; text-align: center;">Hệ thống Quản lý CLB S-Club</h2>
                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                %s
                <hr style="border: 0; border-top: 1px solid #eee; margin: 20px 0;">
                <p style="font-size: 12px; color: #999; text-align: center;">Email này được gửi tự động từ hệ thống S-Club.</p>
            </div>
            """.formatted(bodyHtml);

        dispatch(toEmail, recipientName, subject, htmlContent);
    }

    private void dispatch(String toEmail, String recipientName, String subject, String htmlContent) {
        // Ưu tiên 1: Mailjet HTTP API (Port 443 HTTPS - gửi tới mọi email, không bắt buộc custom domain)
        if (mailjetApiKey != null && !mailjetApiKey.isBlank() && mailjetSecretKey != null && !mailjetSecretKey.isBlank()) {
            sendViaMailjetApi(toEmail, recipientName, subject, htmlContent);
            return;
        }

        // Ưu tiên 2: Brevo HTTP API (Port 443 HTTPS)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            sendViaBrevoApi(toEmail, subject, htmlContent);
            return;
        }

        // Ưu tiên 3: Resend HTTP API (Port 443 HTTPS)
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            sendViaResendApi(toEmail, subject, htmlContent);
            return;
        }

        // Ưu tiên 4: SMTP truyền thống
        if (mailSender != null && mailUsername != null && !mailUsername.isBlank()) {
            sendViaSmtp(toEmail, subject, htmlContent);
            return;
        }

        log.error("Chưa cấu hình MAILJET_API_KEY, BREVO_API_KEY, RESEND_API_KEY hoặc SPRING_MAIL_USERNAME. "
                + "Email '{}' tới {} chưa được gửi đi.", subject, toEmail);
    }

    private void sendViaMailjetApi(String toEmail, String recipientName, String subject, String htmlContent) {
        try {
            log.info("Đang tiến hành gửi email tới {} qua Mailjet HTTP API (Port 443 HTTPS)...", toEmail);
            String url = "https://api.mailjet.com/v3.1/send";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBasicAuth(mailjetApiKey.trim(), mailjetSecretKey.trim());

            Map<String, Object> fromMap = Map.of(
                    "Email", mailjetSenderEmail,
                    "Name", "Hệ Thống S-Club"
            );

            Map<String, Object> toMap = Map.of(
                    "Email", toEmail,
                    "Name", recipientName != null ? recipientName : "Người dùng"
            );

            Map<String, Object> messageMap = Map.of(
                    "From", fromMap,
                    "To", List.of(toMap),
                    "Subject", subject,
                    "HTMLPart", htmlContent
            );

            Map<String, Object> body = Map.of(
                    "Messages", List.of(messageMap)
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ ĐÃ GỬI EMAIL THÀNH CÔNG QUA MAILJET HTTP API TỚI: {}", toEmail);
            } else {
                log.error("❌ KẾT QUẢ GỬI EMAIL QUA MAILJET API THẤT BẠI: {} - {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ LỖI KHI GỬI EMAIL QUA MAILJET HTTP API TỚI {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private void sendViaBrevoApi(String toEmail, String subject, String htmlContent) {
        try {
            log.info("Đang tiến hành gửi email tới {} qua Brevo HTTP API (Port 443 HTTPS)...", toEmail);
            String url = "https://api.brevo.com/v3/smtp/email";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("api-key", brevoApiKey.trim());
            headers.set("accept", "application/json");

            Map<String, Object> body = Map.of(
                    "sender", Map.of("name", "S-Club Platform", "email", brevoSenderEmail),
                    "to", List.of(Map.of("email", toEmail)),
                    "subject", subject,
                    "htmlContent", htmlContent
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ ĐÃ GỬI EMAIL THÀNH CÔNG QUA BREVO HTTP API TỚI: {}", toEmail);
            } else {
                log.error("❌ KẾT QUẢ GỬI EMAIL QUA BREVO API THẤT BẠI: {} - {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ LỖI KHI GỬI EMAIL QUA BREVO HTTP API TỚI {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private void sendViaResendApi(String toEmail, String subject, String htmlContent) {
        try {
            log.info("Đang tiến hành gửi email tới {} qua Resend HTTP API (Port 443 HTTPS)...", toEmail);
            String url = "https://api.resend.com/emails";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey.trim());

            Map<String, Object> body = Map.of(
                    "from", "S-Club <" + resendFromEmail + ">",
                    "to", List.of(toEmail),
                    "subject", subject,
                    "html", htmlContent
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ ĐÃ GỬI EMAIL THÀNH CÔNG QUA RESEND HTTP API TỚI: {}", toEmail);
            } else {
                log.error("❌ KẾT QUẢ GỬI EMAIL QUA RESEND API THẤT BẠI: {} - {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ LỖI KHI GỬI EMAIL QUA RESEND HTTP API TỚI {}: {}", toEmail, e.getMessage(), e);
        }
    }

    private void sendViaSmtp(String toEmail, String subject, String htmlContent) {
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
