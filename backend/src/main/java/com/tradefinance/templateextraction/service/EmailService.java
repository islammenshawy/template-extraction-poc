package com.tradefinance.templateextraction.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@example.com}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    public void sendInvitation(String toEmail, String token) {
        if (mailSender == null) {
            log.warn("Mail sender not configured. Invitation link: {}/register?token={}", frontendUrl, token);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("You're invited to Template Extraction Platform");
            message.setText(String.format(
                "You've been invited to join the Template Extraction Platform!\n\n" +
                "Click the link below to create your account:\n" +
                "%s/register?token=%s\n\n" +
                "This invitation will expire in 7 days.\n\n" +
                "If you didn't expect this invitation, please ignore this email.",
                frontendUrl, token
            ));

            mailSender.send(message);
            log.info("Invitation email sent to: {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send invitation email to: {}", toEmail, e);
            throw new RuntimeException("Failed to send invitation email", e);
        }
    }
}
