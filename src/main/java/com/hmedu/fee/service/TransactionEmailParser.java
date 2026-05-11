package com.hmedu.fee.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser email thông báo giao dịch từ ngân hàng (Techcombank, VPBank, etc.)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionEmailParser {

    /**
     * DTO chứa thông tin giao dịch được trích xuất từ email
     */
    @Getter
    public static class TransactionInfo {
        private String transactionId;      // Mã giao dịch từ email (nếu có)
        private String transactionCode;    // Mã giao dịch do hệ thống tạo (MTC...)
        private BigDecimal amount;
        private String currency;
        private String senderAccount;
        private String senderName;
        private String receiverAccount;
        private String receiverName;
        private String content;            // Nội dung chuyển khoản
        private LocalDateTime transactionTime;
        private String bankName;
        private String emailSubject;
        private boolean success;           // Giao dịch thành công hay không

        public boolean containsTransactionCode(String code) {
            if (code == null || content == null) return false;
            return content.contains(code);
        }

        @Override
        public String toString() {
            return String.format(
                    "TransactionInfo{bank='%s', amount=%s %s, from='%s', content='%s', code='%s'}",
                    bankName, amount, currency, senderName, content, transactionCode);
        }
    }

    // Patterns cho Techcombank
    private static final Pattern TECHCOMBANK_AMOUNT = Pattern.compile(
            "(?:Số tiền|Amount)\s*[:\-]?\s*([0-9,\.]+)\s*(VND|VNĐ|\u20ab)?",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TECHCOMBANK_SENDER = Pattern.compile(
            "(?:Từ|Từ tài khoản|From)\s*[:\-]?\s*([\d\s]+)\s*-\s*([^\n]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TECHCOMBANK_RECEIVER = Pattern.compile(
            "(?:Đến|Đến tài khoản|To)\s*[:\-]?\s*([\d\s]+)\s*-\s*([^\n]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TECHCOMBANK_CONTENT = Pattern.compile(
            "(?:Nội dung|Description|Content)\s*[:\-]?\s*([^\n]+)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern TECHCOMBANK_TIME = Pattern.compile(
            "(?:Thời gian|Time|Date)\s*[:\-]?\s*(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}[\s\d:]+)",
            Pattern.CASE_INSENSITIVE);

    // Pattern tìm MTC code trong nội dung
    private static final Pattern MTC_CODE_PATTERN = Pattern.compile(
            "MTC\d{6,12}", Pattern.CASE_INSENSITIVE);

    // Pattern tìm số điện thoại
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(84|0)([0-9]{9,10})");

    /**
     * Parse email thông báo giao dịch từ nội dung email
     *
     * @param subject Subject của email
     * @param content Nội dung email (có thể là HTML hoặc text)
     * @param isHtml Có phải HTML không
     * @return TransactionInfo nếu parse thành công, null nếu không phải email giao dịch
     */
    public TransactionInfo parseTransactionEmail(String subject, String content, boolean isHtml) {
        TransactionInfo info = new TransactionInfo();
        info.emailSubject = subject;

        // Chuyển HTML sang text nếu cần
        String textContent = isHtml ? htmlToText(content) : content;

        // Xác định ngân hàng từ subject
        info.bankName = detectBank(subject, textContent);

        // Parse theo từng ngân hàng
        switch (info.bankName.toLowerCase()) {
            case "techcombank":
                parseTechcombank(textContent, info);
                break;
            case "vpbank":
                parseVPBank(textContent, info);
                break;
            case "vietcombank":
                parseVietcombank(textContent, info);
                break;
            default:
                parseGeneric(textContent, info);
        }

        // Tìm MTC code trong nội dung
        findMtcCode(textContent, info);

        // Đánh dấu thành công nếu có số tiền và MTC code
        info.success = info.amount != null && info.transactionCode != null;

        if (info.success) {
            log.info("✅ Đã parse giao dịch: {}", info);
        } else {
            log.debug("⚠️ Không phải email giao dịch hoặc thiếu thông tin: subject='{}'", subject);
        }

        return info;
    }

    /**
     * Parse email thông báo từ Techcombank
     */
    private void parseTechcombank(String content, TransactionInfo info) {
        // Amount
        Matcher amountMatcher = TECHCOMBANK_AMOUNT.matcher(content);
        if (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1)
                    .replaceAll("[,\\.]", "");
            try {
                info.amount = new BigDecimal(amountStr);
                info.currency = amountMatcher.group(2) != null ?
                        amountMatcher.group(2).trim() : "VND";
            } catch (NumberFormatException e) {
                log.warn("⚠️ Không parse được số tiền: {}", amountStr);
            }
        }

        // Sender
        Matcher senderMatcher = TECHCOMBANK_SENDER.matcher(content);
        if (senderMatcher.find()) {
            info.senderAccount = senderMatcher.group(1).trim();
            info.senderName = senderMatcher.group(2).trim();
        }

        // Receiver
        Matcher receiverMatcher = TECHCOMBANK_RECEIVER.matcher(content);
        if (receiverMatcher.find()) {
            info.receiverAccount = receiverMatcher.group(1).trim();
            info.receiverName = receiverMatcher.group(2).trim();
        }

        // Content
        Matcher contentMatcher = TECHCOMBANK_CONTENT.matcher(content);
        if (contentMatcher.find()) {
            info.content = contentMatcher.group(1).trim();
        }

        // Time
        Matcher timeMatcher = TECHCOMBANK_TIME.matcher(content);
        if (timeMatcher.find()) {
            info.transactionTime = parseDateTime(timeMatcher.group(1));
        }

        // Transaction ID từ Techcombank
        Pattern txIdPattern = Pattern.compile(
                "(?:Mã giao dịch|Transaction ID|Ref No)\s*[:\-]?\s*([A-Z0-9]+)",
                Pattern.CASE_INSENSITIVE);
        Matcher txIdMatcher = txIdPattern.matcher(content);
        if (txIdMatcher.find()) {
            info.transactionId = txIdMatcher.group(1).trim();
        }
    }

    /**
     * Parse email thông báo từ VPBank
     */
    private void parseVPBank(String content, TransactionInfo info) {
        // VPBank có format tương tự, có thể tùy chỉnh thêm
        parseGeneric(content, info);
    }

    /**
     * Parse email thông báo từ Vietcombank
     */
    private void parseVietcombank(String content, TransactionInfo info) {
        // Vietcombank có format khác, có thể tùy chỉnh thêm
        parseGeneric(content, info);
    }

    /**
     * Parser chung cho các ngân hàng khác
     */
    private void parseGeneric(String content, TransactionInfo info) {
        // Tìm số tiền
        Pattern amountPattern = Pattern.compile(
                "([0-9]{1,3}(?:,[0-9]{3})+(?:\.[0-9]+)?|[0-9]+(?:\.[0-9]+)?)\s*(VND|VNĐ|\u20ab|d)",
                Pattern.CASE_INSENSITIVE);
        Matcher amountMatcher = amountPattern.matcher(content);
        if (amountMatcher.find()) {
            String amountStr = amountMatcher.group(1).replaceAll(",", "");
            try {
                info.amount = new BigDecimal(amountStr);
                info.currency = "VND";
            } catch (NumberFormatException ignored) {}
        }

        // Tìm nội dung chuyển khoản
        Pattern contentPattern = Pattern.compile(
                "(?:Nội dung|Nội dung CK|Description|Memo|Note)\s*[:\-]?\s*([^\n]{5,100})",
                Pattern.CASE_INSENSITIVE);
        Matcher contentMatcher = contentPattern.matcher(content);
        if (contentMatcher.find()) {
            info.content = contentMatcher.group(1).trim();
        }

        // Tìm tên người gửi
        Pattern senderPattern = Pattern.compile(
                "(?:Từ|From|Người gửi|Sender)\s*[:\-]?\s*([^\n]{2,50})",
                Pattern.CASE_INSENSITIVE);
        Matcher senderMatcher = senderPattern.matcher(content);
        if (senderMatcher.find()) {
            info.senderName = senderMatcher.group(1).trim();
        }
    }

    /**
     * Tìm MTC code trong nội dung
     */
    private void findMtcCode(String content, TransactionInfo info) {
        Matcher matcher = MTC_CODE_PATTERN.matcher(content);
        if (matcher.find()) {
            info.transactionCode = matcher.group().toUpperCase();
        }
    }

    /**
     * Xác định ngân hàng từ subject hoặc content
     */
    private String detectBank(String subject, String content) {
        String text = (subject + " " + content).toLowerCase();

        if (text.contains("techcombank") || text.contains("techcom")) {
            return "Techcombank";
        } else if (text.contains("vpbank") || text.contains("vp bank")) {
            return "VPBank";
        } else if (text.contains("vietcombank") || text.contains("vcb")) {
            return "Vietcombank";
        } else if (text.contains("acb")) {
            return "ACB";
        } else if (text.contains("bidv")) {
            return "BIDV";
        }

        return "Unknown";
    }

    /**
     * Chuyển HTML sang text
     */
    private String htmlToText(String html) {
        try {
            Document doc = Jsoup.parse(html);
            // Giữ lại cả text và các trường quan trọng
            return doc.text();
        } catch (Exception e) {
            log.warn("⚠️ Lỗi parse HTML: {}", e.getMessage());
            return html;
        }
    }

    /**
     * Parse datetime từ string
     */
    private LocalDateTime parseDateTime(String dateStr) {
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
                DateTimeFormatter.ofPattern("dd/MM/yy HH:mm:ss"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        );

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {}
        }

        log.warn("⚠️ Không parse được datetime: {}", dateStr);
        return null;
    }

    /**
     * Kiểm tra email có phải thông báo giao dịch không
     */
    public boolean isTransactionEmail(String subject, String from) {
        String text = (subject + " " + from).toLowerCase();

        // Keywords cho email giao dịch
        List<String> transactionKeywords = List.of(
                "chuyển tiền", "chuyển khoản", "giao dịch", "transaction",
                "thông báo", "notification", "biên lai", "receipt",
                "nộp tiền", "nhận tiền", "thanh toán", "payment"
        );

        return transactionKeywords.stream().anyMatch(text::contains);
    }
}
