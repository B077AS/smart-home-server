package smart.home.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${admin.notification.email}")
    private String notificationEmail;

    @Async
    public void sendGarageTriggerNotification(String triggeredBy, String ipAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(notificationEmail);
            helper.setSubject("🚗 Garage Door Triggered — Smart Home System");

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm:ss"));

            String htmlTemplate = loadTemplate("templates/email/garage-trigger-notification.html");

            String htmlContent = htmlTemplate
                    .replace("${triggeredBy}", triggeredBy)
                    .replace("${timestamp}", timestamp)
                    .replace("${ipAddress}", ipAddress);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Garage trigger notification sent to: {}", notificationEmail);

        } catch (Exception e) {
            log.error("Failed to send garage trigger notification", e);
        }
    }

    @Async
    public void sendGarageDoorOpenNotification() {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(notificationEmail);
            helper.setSubject("🔓 Garage Door Open Detected — Smart Home System");

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm:ss"));

            String htmlTemplate = loadTemplate("templates/email/garage-door-open-notification.html");

            String htmlContent = htmlTemplate
                    .replace("${timestamp}", timestamp);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Garage door open notification sent to: {}", notificationEmail);

        } catch (Exception e) {
            log.error("Failed to send garage door open notification", e);
        }
    }

    @Async
    public void sendSuperAdminLoginNotification(String username, String ipAddress) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(notificationEmail);
            helper.setSubject("🔐 Super Admin Login Alert - Smart Home System");

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("MMMM dd, yyyy 'at' HH:mm:ss"));

            String htmlTemplate = loadTemplate("templates/email/super-admin-login-notification.html");

            String htmlContent = htmlTemplate
                    .replace("${username}", username)
                    .replace("${timestamp}", timestamp)
                    .replace("${ipAddress}", ipAddress);

            helper.setText(htmlContent, true);

            mailSender.send(message);
            log.info("Super admin login notification sent to: {}", notificationEmail);

        } catch (Exception e) {
            log.error("Failed to send super admin login notification", e);
        }
    }

    private String loadTemplate(String templatePath) {
        try {
            ClassPathResource resource = new ClassPathResource(templatePath);
            return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load email template: {}", templatePath, e);
            throw new RuntimeException("Could not load email template", e);
        }
    }
}