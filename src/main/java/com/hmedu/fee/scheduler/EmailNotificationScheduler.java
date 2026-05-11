package com.hmedu.fee.scheduler;

import com.hmedu.fee.config.EmailConfig;
import com.hmedu.fee.entity.FeeCollectionRecord;
import com.hmedu.fee.repository.FeeCollectionRepository;
import com.hmedu.fee.service.EmailService;
import com.hmedu.fee.service.EmailService.EmailMessage;
import com.hmedu.fee.service.TransactionEmailParser;
import com.hmedu.fee.service.TransactionEmailParser.TransactionInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Scheduler để kiểm tra email và cập nhật trạng thái thanh toán
 * Đọc email từ folder được cấu hình và đánh dấu đã đọc sau khi xử lý
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailNotificationScheduler {

    private final EmailConfig emailConfig;
    private final EmailService emailService;
    private final TransactionEmailParser emailParser;
    private final FeeCollectionRepository feeCollectionRepository;

    /**
     * Chạy định kỳ để kiểm tra email mới
     * Default: 5 phút một lần (cấu hình trong application.yml)
     * Chỉ đọc email từ folder được cấu hình (email.folder)
     * Email sẽ được đánh dấu đã đọc (SEEN) sau khi xử lý thành công
     */
    @Scheduled(fixedDelayString = "${email.check-interval:300000}")
    public void checkNewEmails() {
        if (!emailConfig.isEnabled() || !emailConfig.isValid()) {
            log.debug("Email checking is disabled or config invalid");
            return;
        }

        String folderName = emailConfig.getFolder();
        log.info("🔄 Bắt đầu kiểm tra email từ folder '{}'...", folderName);

        try {
            // Đọc email chưa đọc (UNSEEN) từ folder được cấu hình
            List<EmailMessage> newEmails = emailService.readNewEmails();
            log.info("📨 Đã nhận {} email mới từ folder '{}'", newEmails.size(), folderName);

            int processedCount = 0;
            int matchedCount = 0;

            for (EmailMessage email : newEmails) {
                try {
                    // Parse email để tìm MTC code (không cần filter subject/from trước)
                    TransactionInfo transaction = emailParser.parseTransactionEmail(
                            email.subject(), email.content(), email.html());

                    processedCount++;

                    // Nếu không parse được MTC code, bỏ qua email này
                    if (transaction == null || !transaction.isSuccess()) {
                        log.debug("📧 Email không chứa MTC code: {}", email.subject());
                        continue;
                    }

                    // Tìm và cập nhật payment record
                    boolean matched = processTransaction(transaction, email);
                    if (matched) {
                        matchedCount++;
                    }

                } catch (Exception e) {
                    log.error("❌ Lỗi xử lý email '{}': {}", email.subject(), e.getMessage());
                }
            }

            log.info("✅ Hoàn thành kiểm tra email folder '{}': {} giao dịch xử lý, {} khớp với hệ thống",
                    folderName, processedCount, matchedCount);

        } catch (Exception e) {
            log.error("❌ Lỗi kiểm tra email folder '{}': {}", folderName, e.getMessage(), e);
        }
    }

    /**
     * Xử lý giao dịch và cập nhật database
     */
    private boolean processTransaction(TransactionInfo transaction, EmailMessage email) {
        String transactionCode = transaction.getTransactionCode();

        if (transactionCode == null) {
            log.warn("⚠️ Không tìm thấy MTC code trong giao dịch");
            return false;
        }

        // Tìm payment record theo transaction ID
        Optional<FeeCollectionRecord> optionalRecord = feeCollectionRepository.findByTransactionId(transactionCode);

        if (optionalRecord.isEmpty()) {
            log.warn("⚠️ Không tìm thấy payment record với MTC code: {}", transactionCode);
            return false;
        }

        FeeCollectionRecord record = optionalRecord.get();

        // Kiểm tra số tiền có khớp không
        if (transaction.getAmount() != null &&
            transaction.getAmount().compareTo(record.getAmount()) != 0) {
            log.warn("⚠️ Số tiền không khớp: Email={}, DB={}",
                    transaction.getAmount(), record.getAmount());
            // Vẫn cập nhật nhưng log cảnh báo
        }

        // Cập nhật trạng thái thanh toán
        record.setPaymentStatus(FeeCollectionRecord.PaymentStatus.PAID);
        record.setPaymentConfirmedAt(LocalDateTime.now());
        record.setPaymentConfirmationSource("EMAIL_" + transaction.getBankName());

        feeCollectionRepository.save(record);

        log.info("✅ Đã cập nhật thanh toán thành công: MTC={}, Amount={}, From={}, Folder={}",
                transactionCode,
                transaction.getAmount(),
                transaction.getSenderName(),
                emailConfig.getFolder());

        return true;
    }

    /**
     * API để kiểm tra email thủ công (có thể gọi từ controller hoặc admin)
     */
    public void manualCheck() {
        log.info("🔍 Kiểm tra email thủ công từ folder '{}'...", emailConfig.getFolder());
        checkNewEmails();
    }

    /**
     * Kiểm tra lịch sử email trong N ngày gần đây
     * Dùng cho initial sync - đọc tất cả email (kể cả đã đọc)
     */
    public void syncHistoricalEmails(int daysBack) {
        if (!emailConfig.isEnabled() || !emailConfig.isValid()) {
            log.warn("⚠️ Email config không hợp lệ");
            return;
        }

        String folderName = emailConfig.getFolder();
        LocalDateTime since = LocalDateTime.now().minusDays(daysBack);
        log.info("🔄 Đồng bộ email từ folder '{}' từ {} trở về đây", folderName, since);

        // Đọc tất cả email (kể cả đã đọc) trong khoảng thờI gian
        List<EmailMessage> emails = emailService.readAllEmails(since, 100);
        log.info("📨 Tìm thấy {} email trong {} ngày qua từ folder '{}'", 
                emails.size(), daysBack, folderName);

        int matched = 0;
        for (EmailMessage email : emails) {
            try {
                // Parse email để tìm MTC code
                TransactionInfo transaction = emailParser.parseTransactionEmail(
                        email.subject(), email.content(), email.html());

                if (transaction != null && transaction.isSuccess()) {
                    if (processTransaction(transaction, email)) {
                        matched++;
                    }
                }
            } catch (Exception e) {
                log.debug("📧 Bỏ qua email không chứa MTC code: {}", email.subject());
            }
        }

        log.info("✅ Đồng bộ hoàn tất folder '{}': {} giao dịch khớp", folderName, matched);
    }
}
