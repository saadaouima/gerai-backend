package com.gerai_backend.gerai.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendTemporaryPassword(String toEmail,
                                      String firstName,
                                      String username,
                                      String temporaryPassword) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Welcome — Your Account Has Been Created");
            message.setText(buildEmailBody(firstName, username, temporaryPassword));

            mailSender.send(message);
            log.info("Temporary password email sent to: {}", toEmail);

        } catch (Exception ex) {
            // Log but don't fail the whole request — employee is already created
            log.error("Failed to send email to {}: {}", toEmail, ex.getMessage());
        }
    }

    private String buildEmailBody(String firstName,
                                  String username,
                                  String temporaryPassword) {
        return """
                Dear %s,

                Your HR system account has been created.

                Username          : %s
                Temporary Password: %s

                Please log in and change your username and password immediately.
                Your temporary password will expire after first login.

                Regards,
                HR Department
                """.formatted(firstName, username, temporaryPassword);
    }
}