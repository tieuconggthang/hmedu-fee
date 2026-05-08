package com.hmedu.fee.service;

import com.hmedu.fee.config.AppConfig;
import com.hmedu.fee.config.VietQRConfig;
import com.hmedu.fee.config.ZaloConfig;
import com.hmedu.fee.dto.StudentFeeDto;
import com.hmedu.fee.entity.ExcelFileStatus;
import com.hmedu.fee.entity.FeeCollectionRecord;
import com.hmedu.fee.repository.ExcelFileStatusRepository;
import com.hmedu.fee.repository.FeeCollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.FilenameFilter;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeeCollectionService {

    private final ExcelProcessingService excelService;
    private final VietQRService vietQRService;
    private final ZaloService zaloService;
    private final FeeCollectionRepository repository;
    private final ExcelFileStatusRepository fileStatusRepository;
    private final AppConfig appConfig;
    private final VietQRConfig vietQRConfig;
    private final ZaloConfig zaloConfig;

    /**
     * Chạy định kỳ - Quét thư mục và xử lý các file Excel chưa xử lý
     */
    @Scheduled(cron = "${hmedu.fee-collection.scheduler.cron:0 0 8 1 * ?}")
    @Transactional
    public void processMonthlyFeeCollection() {
        log.info("=========================================");
        log.info("Starting fee collection process - Scanning directory");
        log.info("=========================================");

        // Kiểm tra Zalo API login status
        if (zaloConfig.isEnabled() && !zaloService.checkLoginStatus()) {
            log.error("Zalo API is not logged in. Please login first at: {}/login/qr/web", zaloConfig.getApiUrl());
            return;
        }

        // Lấy thư mục data từ config
        String dataDir = getDataDirectory();
        log.info("Scanning directory: {}", dataDir);

        // Tìm tất cả file .xlsx trong thư mục
        File dir = new File(dataDir);
        File[] excelFiles = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.toLowerCase().endsWith(".xlsx");
            }
        });

        if (excelFiles == null || excelFiles.length == 0) {
            log.warn("No Excel files found in directory: {}", dataDir);
            return;
        }

        log.info("Found {} Excel file(s) to process", excelFiles.length);

        // Xử lý từng file
        for (File excelFile : excelFiles) {
            try {
                processExcelFile(excelFile);
            } catch (Exception e) {
                log.error("Error processing file: {}", excelFile.getName(), e);
            }
        }

        log.info("=========================================");
        log.info("Fee collection process completed");
        log.info("=========================================");
    }

    /**
     * Xử lý một file Excel cụ thể
     */
    @Transactional
    public void processExcelFile(File excelFile) {
        String fileName = excelFile.getName();
        log.info("Processing file: {}", fileName);

        // Kiểm tra file đã xử lý chưa
        Optional<ExcelFileStatus> existingStatus = fileStatusRepository.findByFileName(fileName);
        
        if (existingStatus.isPresent()) {
            ExcelFileStatus status = existingStatus.get();
            if (status.getStatus() == ExcelFileStatus.FileStatus.COMPLETED) {
                log.info("File {} already completed. Skipping.", fileName);
                return;
            }
            log.info("File {} was processed partially ({} rows). Resuming...", 
                fileName, status.getProcessedRows());
        }

        // Tạo hoặc cập nhật trạng thái file
        ExcelFileStatus fileStatus = existingStatus.orElse(
            ExcelFileStatus.builder()
                .fileName(fileName)
                .filePath(excelFile.getAbsolutePath())
                .status(ExcelFileStatus.FileStatus.PROCESSING)
                .processedRows(0)
                .build()
        );
        fileStatus.setStatus(ExcelFileStatus.FileStatus.PROCESSING);
        fileStatusRepository.save(fileStatus);

        try {
            // Đọc danh sách từ file
            List<StudentFeeDto> students = excelService.readFeeDataFromFile(excelFile.getAbsolutePath());
            log.info("File {}: Found {} students", fileName, students.size());
            
            fileStatus.setTotalRows(students.size());
            int processedCount = 0;

            // Xử lý từng học sinh
            for (StudentFeeDto student : students) {
                try {
                    // Kiểm tra đã xử lý dòng này chưa (qua transactionId)
                    if (isStudentProcessed(student.getTransactionId())) {
                        log.debug("Student {} already processed (transactionId: {})", 
                            student.getStudentName(), student.getTransactionId());
                        processedCount++;
                        continue;
                    }
                    
                    processStudent(student);
                    processedCount++;
                    
                    // Cập nhật tiến độ
                    fileStatus.setProcessedRows(processedCount);
                    fileStatusRepository.save(fileStatus);
                    
                } catch (Exception e) {
                    log.error("Error processing student: {} from file {}", 
                        student.getStudentName(), fileName, e);
                }
            }

            // Đánh dấu file đã xử lý xong
            fileStatus.setStatus(ExcelFileStatus.FileStatus.COMPLETED);
            fileStatus.setProcessedAt(LocalDateTime.now());
            fileStatusRepository.save(fileStatus);
            
            log.info("File {} processed successfully: {}/{} students", 
                fileName, processedCount, students.size());
                
        } catch (Exception e) {
            log.error("Error processing file: {}", fileName, e);
            fileStatus.setStatus(ExcelFileStatus.FileStatus.FAILED);
            fileStatusRepository.save(fileStatus);
        }
    }

    /**
     * Kiểm tra học sinh đã được xử lý chưa (qua transactionId)
     */
    private boolean isStudentProcessed(String transactionId) {
        return repository.findByTransactionId(transactionId).isPresent();
    }

    /**
     * Xử lý từng học sinh
     */
    @Transactional
    public void processStudent(StudentFeeDto student) {
        String monthYear = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM/yyyy"));

        log.info("Processing student: {} - Amount: {} - TransactionId: {}", 
            student.getStudentName(), student.getAmount(), student.getTransactionId());

        // 1. Tạo VietQR
        String qrUrl = generateVietQR(student);
        student.setQrCodeUrl(qrUrl);

        // 2. Lưu vào DB
        FeeCollectionRecord record = saveToDatabase(student, monthYear);

        // 3. Gửi Zalo (nếu enabled)
        if (zaloConfig.isEnabled() && student.getPhone() != null && !student.getPhone().isEmpty()) {
            String caption = buildCaption(student, monthYear);
            boolean sent = zaloService.sendImage(student.getPhone(), student.getQrCodeUrl(), caption);
            
            if (sent) {
                record.setZaloMessageSent(true);
                record.setZaloSentAt(LocalDateTime.now());
                record.setPaymentStatus(FeeCollectionRecord.PaymentStatus.NOTIFIED);
                log.info("Zalo message with QR sent to: {}", student.getStudentName());
            } else {
                log.warn("Failed to send Zalo message to: {}", student.getStudentName());
            }
            repository.save(record);
        }
    }

    /**
     * Tạo VietQR URL
     */
    private String generateVietQR(StudentFeeDto student) {
        String bankId = vietQRService.getBankId(student.getBank());
        String accountNumber = student.getAccountNumber();
        String accountName = student.getAccountName();
        BigDecimal amount = student.getAmount();
        String content = buildTransferContent(student);

        if (accountNumber == null || accountNumber.isEmpty()) {
            accountNumber = vietQRConfig.getDefaultAccount();
            accountName = vietQRConfig.getDefaultAccountName();
            bankId = vietQRConfig.getDefaultBankId();
        }

        return vietQRService.generateDynamicQRUrl(
            bankId, accountNumber, accountName, amount, content
        );
    }

    /**
     * Tạo nội dung chuyển khoản
     */
    private String buildTransferContent(StudentFeeDto student) {
        String originalContent = removeVietnameseAccents(student.getContent());
        String transactionId = "MTC" + student.getTransactionId();
        String combined = originalContent + " " + transactionId;
        
        if (combined.length() > 25) {
            int maxOriginalLength = 25 - transactionId.length() - 1;
            if (maxOriginalLength > 0) {
                String shortContent = originalContent.substring(0, 
                    Math.min(originalContent.length(), maxOriginalLength));
                combined = shortContent + " " + transactionId;
            } else {
                combined = transactionId;
            }
        }
        return combined.trim();
    }

    private String removeVietnameseAccents(String text) {
        if (text == null || text.isEmpty()) return "";
        String result = text;
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
            removeVietnameseAccents(student.getContent()) + " MTC" + student.getTransactionId(),
            student.getTransactionId()
        );
    }

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
            .transactionId(student.getTransactionId())
            .build();
        return repository.save(record);
    }

    private String formatCurrency(BigDecimal amount) {
        if (amount == null) return "0";
        return String.format("%,d", amount.longValue());
    }

    private String getDataDirectory() {
        String filePath = appConfig.getExcel().getFilePath();
        File file = new File(filePath);
        return file.getParent() != null ? file.getParent() : "./data";
    }

    public void triggerManually() {
        processMonthlyFeeCollection();
    }
}
