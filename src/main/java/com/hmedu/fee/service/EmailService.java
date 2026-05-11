package com.hmedu.fee.service;

import com.hmedu.fee.config.EmailConfig;
import jakarta.mail.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * Service để đọc email từ Gmail qua IMAP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailConfig emailConfig;
    private final Session mailSession;

    /**
     * DTO chứa thông tin email đơn giản
     */
    public record EmailMessage(
            String messageId,
            String subject,
            String from,
            Date receivedDate,
            String content,
            boolean html
    ) {}

    /**
     * Đọc email mới từ folder được cấu hình
     * Chỉ đọc email chưa đọc (UNSEEN)
     *
     * @return Danh sách email mới
     */
    public List<EmailMessage> readNewEmails() {
        if (!emailConfig.isValid()) {
            log.warn("⚠️ Email config invalid, skipping email check");
            return List.of();
        }

        if (!emailConfig.isEnabled()) {
            log.debug("Email checking is disabled");
            return List.of();
        }

        List<EmailMessage> messages = new ArrayList<>();
        Store store = null;
        Folder folder = null;

        try {
            store = mailSession.getStore("imaps");
            store.connect(emailConfig.getHost(), emailConfig.getUsername(), emailConfig.getPassword());

            folder = store.getFolder(emailConfig.getFolder());
            if (!folder.exists()) {
                log.error("❌ Folder '{}' không tồn tại!", emailConfig.getFolder());
                // List available folders
                listAvailableFolders(store);
                return messages;
            }

            folder.open(Folder.READ_WRITE);

            // Tìm email chưa đọc (UNSEEN)
            Message[] unreadMessages = folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));

            log.info("📧 Tìm thấy {} email chưa đọc trong folder '{}'", unreadMessages.length, emailConfig.getFolder());

            for (Message message : unreadMessages) {
                try {
                    EmailMessage emailMessage = parseMessage(message);
                    messages.add(emailMessage);

                    // Đánh dấu đã đọc sau khi xử lý thành công
                    message.setFlag(Flags.Flag.SEEN, true);

                    log.debug("✅ Đã xử lý email: {} - {}",
                            emailMessage.messageId(), emailMessage.subject());

                } catch (Exception e) {
                    log.error("❌ Lỗi parse email: {}", e.getMessage());
                    // Không đánh dấu SEEN để thử lại sau
                }
            }

        } catch (MessagingException e) {
            log.error("❌ Lỗi kết nối email: {}", e.getMessage());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }

        return messages;
    }

    /**
     * Đọc tất cả email từ folder (không chỉ UNSEEN)
     * Dùng cho initial sync hoặc debug
     *
     * @param sinceDate Chỉ đọc email từ ngày này trở đi
     * @param maxResults Giới hạn số lượng kết quả
     */
    public List<EmailMessage> readAllEmails(LocalDateTime sinceDate, int maxResults) {
        if (!emailConfig.isValid()) {
            return List.of();
        }

        List<EmailMessage> messages = new ArrayList<>();
        Store store = null;
        Folder folder = null;

        try {
            store = mailSession.getStore("imaps");
            store.connect(emailConfig.getHost(), emailConfig.getUsername(), emailConfig.getPassword());

            folder = store.getFolder(emailConfig.getFolder());
            folder.open(Folder.READ_ONLY);

            Message[] allMessages = folder.getMessages();
            log.info("📧 Tổng cộng {} email trong folder '{}'", allMessages.length, emailConfig.getFolder());

            // Lọc theo ngày nếu cần
            Date since = sinceDate != null
                    ? Date.from(sinceDate.atZone(ZoneId.systemDefault()).toInstant())
                    : null;

            int count = 0;
            for (int i = allMessages.length - 1; i >= 0 && count < maxResults; i--) {
                Message message = allMessages[i];

                if (since != null && message.getReceivedDate() != null
                        && message.getReceivedDate().before(since)) {
                    continue;
                }

                try {
                    EmailMessage emailMessage = parseMessage(message);
                    messages.add(emailMessage);
                    count++;
                } catch (Exception e) {
                    log.error("❌ Lỗi parse email #{}: {}", i, e.getMessage());
                }
            }

        } catch (MessagingException e) {
            log.error("❌ Lỗi kết nối email: {}", e.getMessage());
        } finally {
            closeQuietly(folder);
            closeQuietly(store);
        }

        return messages;
    }

    /**
     * Parse Message thành EmailMessage
     */
    private EmailMessage parseMessage(Message message) throws MessagingException, IOException {
        String messageId = Arrays.toString(message.getHeader("Message-ID"));
        String subject = message.getSubject();
        String from = getFromAddress(message);
        Date receivedDate = message.getReceivedDate();

        // Lấy nội dung email
        String content = extractContent(message);
        boolean isHtml = message.isMimeType("text/html")
                || (message.isMimeType("multipart/*")
                && content.trim().startsWith("<"));

        return new EmailMessage(messageId, subject, from, receivedDate, content, isHtml);
    }

    /**
     * Trích xuất nội dung text từ Message
     */
    private String extractContent(Part part) throws MessagingException, IOException {
        if (part.isMimeType("text/plain")) {
            return (String) part.getContent();
        } else if (part.isMimeType("text/html")) {
            return (String) part.getContent();
        } else if (part.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) part.getContent();

            // Ưu tiên text/plain, fallback text/html
            String htmlContent = null;

            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart bodyPart = multipart.getBodyPart(i);
                if (bodyPart.isMimeType("text/plain")) {
                    return (String) bodyPart.getContent();
                } else if (bodyPart.isMimeType("text/html")) {
                    htmlContent = (String) bodyPart.getContent();
                }
            }

            return htmlContent != null ? htmlContent : "";
        }

        return "";
    }

    /**
     * Lấy địa chỉ người gửi
     */
    private String getFromAddress(Message message) throws MessagingException {
        Address[] from = message.getFrom();
        if (from != null && from.length > 0) {
            return from[0].toString();
        }
        return "unknown";
    }

    /**
     * Liệt kê các folder có sẵn
     */
    private void listAvailableFolders(Store store) {
        try {
            Folder[] folders = store.getDefaultFolder().list("*");
            log.info("📂 Các folder có sẵn:");
            for (Folder f : folders) {
                log.info("   - {} (type: {})", f.getFullName(),
                        f.getType() == Folder.HOLDS_FOLDERS ? "FOLDER" : "MAILBOX");
            }
        } catch (MessagingException e) {
            log.error("❌ Không thể liệt kê folder: {}", e.getMessage());
        }
    }

    private void closeQuietly(Folder folder) {
        if (folder != null && folder.isOpen()) {
            try {
                folder.close(false);
            } catch (MessagingException e) {
                log.debug("Lỗi đóng folder: {}", e.getMessage());
            }
        }
    }

    private void closeQuietly(Store store) {
        if (store != null && store.isConnected()) {
            try {
                store.close();
            } catch (MessagingException e) {
                log.debug("Lỗi đóng store: {}", e.getMessage());
            }
        }
    }
}
