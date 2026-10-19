package com.chnu.seabattle.service;

import com.chnu.seabattle.enums.EmailTokenPurpose;
import com.chnu.seabattle.exception.RateLimitException;
import com.chnu.seabattle.exception.ServerErrorException;
import com.chnu.seabattle.repository.redis.EmailRateLimitRedisRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailRateLimitRedisRepository emailRateLimitRedisRepository;

    @Value("${mail.from:Sea Battle <noreply@seabattle.lol>}")
    private String fromAddress;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    public void checkRateLimit(UUID userId, EmailTokenPurpose purpose) {
        Duration retryAfter = emailRateLimitRedisRepository.retryAfter(purpose, userId, Instant.now().toEpochMilli());
        if (!Duration.ZERO.equals(retryAfter)) {
            throw new RateLimitException(
                    "Too many requests, try again in " + retryAfter.toSeconds() + " seconds",
                    retryAfter.toSeconds()
            );
        }
    }

    public void sendVerificationEmail(UUID userId, String emailTo, String username, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;
        String body = buildBody(
                username,
                "Confirm your email address to finish setting up your Sea Battle account.",
                "Verify email",
                link,
                "This link expires in 24 hours and can be used once."
        );
        send(EmailTokenPurpose.VERIFY_EMAIL, userId, emailTo, "Verify your Sea Battle email", body);
    }

    public void sendResetEmail(UUID userId, String emailTo, String username, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;
        String body = buildBody(
                username,
                "We received a request to reset your Sea Battle password.",
                "Reset password",
                link,
                "This link expires in 1 hour and can be used once. If you did not ask for this, you can ignore this email."
        );
        send(EmailTokenPurpose.RESET_PASSWORD, userId, emailTo, "Reset your Sea Battle password", body);
    }

    private static String buildBody(String username, String message, String buttonText, String link, String footer) {
        return """
                <div style="font-family:Arial,Helvetica,sans-serif;max-width:480px;margin:0 auto;padding:24px;border:3px solid #111;">
                  <h2 style="margin:0 0 16px;">Sea Battle</h2>
                  <p>Hi %s,</p>
                  <p>%s</p>
                  <p><a href="%s" style="display:inline-block;padding:12px 20px;background:#111;color:#fff;text-decoration:none;font-weight:bold;">%s</a></p>
                  <p style="font-size:13px;color:#555;">If the button does not work, copy this link into your browser:<br>%s</p>
                  <p style="font-size:13px;color:#555;">%s</p>
                </div>
                """.formatted(HtmlUtils.htmlEscape(username), message, link, buttonText, link, footer);
    }

    private void send(EmailTokenPurpose purpose, UUID userId, String emailTo, String subject, String htmlBody) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(emailTo);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(mimeMessage);
            emailRateLimitRedisRepository.recordSend(purpose, userId, Instant.now().toEpochMilli());
            log.info("{} email successfully sent to: {}", purpose, emailTo);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send {} email to {}: {}", purpose, emailTo, e.getMessage());
            throw new ServerErrorException("Failed to send " + purpose + " email: " + e.getMessage(), e);
        }
    }
}
