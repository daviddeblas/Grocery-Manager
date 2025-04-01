package com.grocerymanager.api.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

/**
 * Service to handle email sending operations.
 */
@Service
public class EmailService {
    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * Sends an email with the user's credentials.
     *
     * @param to Recipient email address
     * @param username User's username
     * @param tempPassword Temporary password
     */
    @Async
    public void sendCredentialsEmail(String to, String username, String tempPassword) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(senderEmail, "Grocery Manager");
            helper.setTo(to);
            helper.setSubject("Grocery Manager - Your Login Credentials");

            // Prepare email content with HTML formatting
            String emailContent =
                    "<html><body>" +
                            "<p>Hello,</p>" +
                            "<p>Here are your login credentials for the Grocery Manager application:</p>" +
                            "<p><strong>Username:</strong> " + username + "</p>" +
                            "<p><strong>New Password:</strong> " + tempPassword + "</p>" +
                            "</body></html>";

            helper.setText(emailContent, true);

            mailSender.send(message);
            logger.info("Credentials email sent to: {}", to);
        } catch (MessagingException e) {
            logger.error("Failed to send credentials email to: {}", to, e);
        } catch (UnsupportedEncodingException e) {
            logger.error("Failed to send credentials email to: {}", to, e);
            throw new RuntimeException(e);
        }
    }
}