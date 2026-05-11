package com.hmedu.fee.config;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

/**
 * Cấu hình kết nối Gmail IMAP để đọc email thông báo giao dịch
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "email")
@Getter
@Setter
public class EmailConfig {

    /**
     * Gmail address (e.g., your-email@gmail.com)
     */
    private String username;

    /**
     * App Password (NOT your regular Gmail password)
     * Generate at: https://myaccount.google.com/apppasswords
     */
    private String password;

    /**
     * IMAP Server host
     */
    private String host = "imap.gmail.com";

    /**
     * IMAP Port (SSL)
     */
    private int port = 993;

    /**
     * Folder to read emails from (e.g., "INBOX", "Thông báo giao dịch", "Techcombank", "biendongsodu")
     */
    private String folder = "biendongsodu";

    /**
     * Enable/disable email checking
     */
    private boolean enabled = true;

    /**
     * How often to check emails (in minutes)
     */
    private int checkIntervalMinutes = 5;

    @Bean
    public Session mailSession() {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", host);
        props.put("mail.imaps.port", String.valueOf(port));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", "*");

        // Connection settings
        props.put("mail.imaps.connectiontimeout", "10000");
        props.put("mail.imaps.timeout", "10000");

        // Enable debugging for troubleshooting (set to false in production)
        props.put("mail.debug", "false");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        log.info("✉️ Email session configured for: {} (folder: {})", username, folder);
        return session;
    }

    /**
     * Validate configuration
     */
    public boolean isValid() {
        boolean valid = username != null && !username.isEmpty()
                && password != null && !password.isEmpty();

        if (!valid) {
            log.warn("⚠️ Email configuration incomplete. Set email.username and email.password");
        }
        return valid;
    }
}
