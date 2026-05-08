package com.hmedu.fee.service;

import com.hmedu.fee.config.AppConfig;
import com.hmedu.fee.config.VietQRConfig;
import com.hmedu.fee.config.ZaloConfig;
import com.hmedu.fee.dto.StudentFeeDto;
import com.hmedu.fee.entity.FeeCollectionRecord;
import com.hmedu.fee.repository.FeeCollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeCollectionService {

    private final ExcelProcessingService excelService;
    private final VietQRService vietQRService;
    private final ZaloService zaloService;
    private final FeeCollectionRepository repository;
    private final AppConfig appConfig;
    private final VietQRConfig vietQRConfig;
    private final ZaloConfig zaloConfig;

    /**
     * Chạy định kỳ theo cron schedule
     */
    @Scheduled(cron = "${hmedu.fee-collection.scheduler.cron:0 0 8 1 * ?}")
    @Transactional
    public void processMonthlyFeeCollection() {
        log.info("=========================================");
        log.info("Starting monthly fee collection process");
        log.info("=========================================");

        // Kiểm tra Zalo API login status
        if (zaloConfig.isEnabled() && !zaloService.checkLoginStatus()) {
            log.error("Zalo API is not logged in. Please login first at: {}/login/qr/web", zaloConfig.getApiUrl());
            return;
        }

        // Đọc danh sách từ Excel
        List<StudentFeeDto> students = excelService.readFeeData();
        log.info("Found {} students to process", students.size());

        // Xử lý từng học sinh
        for (StudentFeeDto student : students) {
            try {
                processStudent(student);
            } catch (Exception e) {
                log.error("Error processing student: {}", student.getStudentName(), e);
            }
        }

        log.info("=========================================");
        log.info("Monthly fee collection process completed");
        log.info("=========================================");
    }

    /**
     * Xử lý từng học sinh
     */
    @Transactional
    public void processStudent(StudentFeeDto student) {
        String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/yyyy"));

        // Kiểm tra đã xử lý chưa
        Optional<FeeCollectionRecord> existing = repository
            .findByPhoneNumberAndMonthYear(student.getPhone(), monthYear);

        if (existing.isPresent()) {
            log.info("Student {} already processed for {}", student.getStudentName(), monthYear);
            return;
        }

        log.info("Processing student: {} - Amount: {}", student.getStudentName(), student.getAmount());

        // 1. Tạo VietQR
        String qrUrl = generateVietQR(student);
        student.setQrCodeUrl(qrUrl);

        // 2. Lưu vào DB
        FeeCollectionRecord record = saveToDatabase(student, monthYear);

        // 3. Gửi Zalo (nếu enabled)
        if (zaloConfig.isEnabled() && student.getPhone() != null && !student.getPhone().isEmpty()) {
            // Gửi ảnh QR + caption
            String caption = buildCaption(student, monthYear);
            boolean sent = zaloService.sendImage(student.getPhone(), student.getQrCodeUrl(), caption);
            
            if (sent) {
                record.setZaloMessageSent(true);
                record.setZaloSentAt(LocalDateTime.now());
                record.setPaymentStatus(FeeCollectionRecord.PaymentStatus.NOTIFIED);
                log.info("Zalo message with QR sent to: {}", student.getStudentName());
            } else {
                // Fallback: gửi text nếu gửi ảnh thất bại
                log.warn("Failed to send image, trying text message...");
                String textMessage = buildTextMessage(student, monthYear);
                sent = zaloService.sendMessage(student.getPhone(), textMessage);
                if (sent) {
                    record.setZaloMessageSent(true);
                    record.setZaloSentAt(LocalDateTime.now());
                    record.setPaymentStatus(FeeCollectionRecord.PaymentStatus.NOTIFIED);
                }
            }
            repository.save(record);
        }
    }

    /**
     * Loại bỏ dấu tiếng Việt, chuyển về không dấu
     * VD: "Học phí tháng 4" -> "Hoc phi thang 4"
     */
    private String removeVietnameseAccents(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        
        String result = text;
        // Chuyển đổi ký tự có dấu sang không dấu
        result = result.replaceAll("[àáạảãâầấậẩẫăằắặẳẵ]", "a");
        result = result.replaceAll("[ÀÁẠẢÃÂẦẤẨẪĂẰẮẶẲẴ]", "A");
        result = result.replaceAll("[èéẹẻẽêềếệểễ]", "e");
        result = result.replaceAll("[ÈÉẸẺẼÊỀẾỆỂỄ]", "E");
        result = result.replaceAll("[ìíịỉĩ]", "i");
        result = result.replaceAll("[ÌÍỊỈĨ]", "I");
        result = result.replaceAll("[òóọỏõôồốộổỗơờớợởỡ]", "o");
        result = result.replaceAll("[ÒÓỌỎÕÔỒỐỘỔỖƠỜỚỢỞỠ]", "O");
        result = result.replaceAll("[ùúụủũưừứựửữ]", "u");
        result = result.replaceAll("[ÙÚỤỦŨƯỪỨỰỬỮ]", "U");
        result = result.replaceAll("[ỳýỵỷỹ]", "y");
        result = result.replaceAll("[ỲÝỴỶỸ]", "Y");
        result = result.replaceAll("[đ]", "d");
        result = result.replaceAll("[Đ]", "D");
        
        return result;
    }

    /**
     * Tạo nội dung chuyển khoản kết hợp: nội dung gốc (không dấu) + mã giao dịch
     * VD: "BuiKhanhAn0396935585 Hoc phi thang4 MTC2505080004"
     */
    private String buildTransferContent(StudentFeeDto student) {
        String originalContent = removeVietnameseAccents(student.getContent());  // Bỏ dấu
        String transactionId = "MTC" + student.getTransactionId();  // Thêm MTC vào mã
        
        // Kết hợp: nội dung gốc + mã giao dịch
        String combined = originalContent + " " + transactionId;
        
        // Giới hạn 25 ký tự (VietQR), nếu quá dài thì cắt nội dung gốc
        if (combined.length() > 25) {
            int maxOriginalLength = 25 - transactionId.length() - 1; // -1 cho dấu cách
            if (maxOriginalLength > 0) {
                String shortContent = originalContent.substring(0, Math.min(originalContent.length(), maxOriginalLength));
                combined = shortContent + " " + transactionId;
            } else {
                combined = transactionId; // Chỉ gửi mã nếu không đủ chỗ
            }
        }
        
        return combined.trim();
    }

    /**
     * Tạo VietQR URL - Nội dung kết hợp nội dung gốc + mã giao dịch
     */
    private String generateVietQR(StudentFeeDto student) {
        String bankId = vietQRService.getBankId(student.getBank());
        String accountNumber = student.getAccountNumber();
        String accountName = student.getAccountName();
        BigDecimal amount = student.getAmount();
        // Nội dung CK trong QR: kết hợp nội dung gốc + mã giao dịch
        String transferContent = buildTransferContent(student);

        // Nếu thiếu thông tin, dùng default
        if (accountNumber == null || accountNumber.isEmpty()) {
            accountNumber = vietQRConfig.getDefaultAccount();
            accountName = vietQRConfig.getDefaultAccountName();
            bankId = vietQRConfig.getDefaultBankId();
        }

        return vietQRService.generateDynamicQRUrl(
            bankId, accountNumber, accountName, amount, transferContent  // Nội dung kết hợp
        );
    }

    /**
     * Tạo caption ngắn cho ảnh QR
     */
    private String buildCaption(StudentFeeDto student, String monthYear) {
        return String.format(
            "🎓 THU HỌC PHÍ THÁNG %s\n\n" +
            "Kính gửi phụ huynh học sinh %s,\n\n" +
            "• Học phí: %s VNĐ\n" +
            "• Nội dung CK: %s\n" +
            "• Mã tham chiếu: MTC%s\n\n" +
            "Vui lòng ghi đúng nội dung CK khi thanh toán.\n\n" +
            "Quét mã QR để thanh toán nhanh chóng.\nXin cảm ơn!\nHMEDU",
            monthYear,
            student.getStudentName(),
            formatCurrency(student.getAmount()),
            removeVietnameseAccents(student.getContent()) + " MTC" + student.getTransactionId(),  // Nội dung không dấu + MTC
            student.getTransactionId()  // Mã số
        );
    }
    
    /**
     * Tạo tin nhắn text đầy đủ (fallback khi gửi ảnh thất bại)
     */
    private String buildTextMessage(StudentFeeDto student, String monthYear) {
        return String.format(
            "🚀 THU HỌC PHÍ HMEDU\n\n" +
            "Kính gửi phụ huynh học sinh %s,\n\n" +
            "Học phí tháng %s:\n" +
            "💰 Số tiền: %s VNĐ\n" +
            "📝 Nội dung: %s\n\n" +
            "Quý phụ huynh vui lòng chuyển khoản:\n" +
            "🏦 Ngân hàng: %s\n" +
            "💳 Số TK: %s\n" +
            "👤 Tên TK: %s\n" +
            "📝 Nội dung: %s\n\n" +
            "Hoặc quét mã QR tại: %s\n\n" +
            "Xin cảm ơn!\nHMEDU",
            student.getStudentName(),
            monthYear,
            formatCurrency(student.getAmount()),
            student.getContent(),
            student.getBank(),
            student.getAccountNumber(),
            student.getAccountName(),
            student.getContent(),
            student.getQrCodeUrl()
        );
    }

    /**
     * Gửi thông báo Zalo (cũ - giữ lại để tương thích)
     */
    private boolean sendZaloNotification(StudentFeeDto student, FeeCollectionRecord record) {
        String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/yyyy"));
        String caption = buildCaption(student, monthYear);
        return zaloService.sendImage(student.getPhone(), student.getQrCodeUrl(), caption);
    }

    /**
     * Lưu vào database
     */
    private FeeCollectionRecord saveToDatabase(StudentFeeDto student, String monthYear) {
        FeeCollectionRecord record = FeeCollectionRecord.builder()
            .studentName(student.getStudentName())
            .phoneNumber(student.getPhone())
            .amount(student.getAmount())
            .content(student.getContent())
            .accountName(student.getAccountName())
            .accountNumber(student.getAccountNumber())
            .bankName(student.getBank())
            .bankId(student.getBankId())
            .qrCodeUrl(student.getQrCodeUrl())
            .paymentStatus(FeeCollectionRecord.PaymentStatus.PENDING)
            .monthYear(monthYear)
            .transactionId(student.getTransactionId())  // Lưu mã định danh giao dịch
            .build();

        return repository.save(record);
    }

    /**
     * Format số tiền thành chuỗi đọc được
     */
    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,d", amount.longValue());
    }

    /**
     * Manual trigger (for testing)
     */
    public void triggerManually() {
        processMonthlyFeeCollection();
    }
}
