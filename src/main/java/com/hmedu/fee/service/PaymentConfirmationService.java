package com.hmedu.fee.service;

import com.hmedu.fee.entity.FeeCollectionRecord;
import com.hmedu.fee.repository.FeeCollectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Service xử lý xác nhận thanh toán từ ngân hàng
 * Dùng để match thông báo chuyển khoản với transactionId
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentConfirmationService {

    private final FeeCollectionRepository repository;

    /**
     * Xác nhận thanh toán bằng transactionId (nội dung CK từ cột G)
     * 
     * @param transactionId Mã định danh giao dịch (VD: "HocPhiThang4_BuiKhanhAn")
     * @param amount Số tiền thanh toán
     * @param source Nguồn thông báo (email, sms, api...)
     * @return true nếu tìm thấy và cập nhật thành công
     */
    @Transactional
    public boolean confirmPaymentByTransactionId(String transactionId, BigDecimal amount, String source) {
        log.info("Confirming payment for transactionId: {}, amount: {}, source: {}", 
            transactionId, amount, source);
        
        Optional<FeeCollectionRecord> recordOpt = repository.findByTransactionId(transactionId);
        
        if (recordOpt.isEmpty()) {
            log.warn("No record found for transactionId: {}", transactionId);
            return false;
        }
        
        FeeCollectionRecord record = recordOpt.get();
        
        // Kiểm tra số tiền có khớp không (cho phép sai lệch nhỏ)
        if (record.getAmount() != null && amount != null) {
            BigDecimal diff = record.getAmount().subtract(amount).abs();
            if (diff.compareTo(new BigDecimal("1000")) > 0) { // Sai lệch > 1000đ
                log.warn("Amount mismatch for transactionId: {}. Expected: {}, Received: {}",
                    transactionId, record.getAmount(), amount);
            }
        }
        
        // Cập nhật trạng thái
        record.setPaymentStatus(FeeCollectionRecord.PaymentStatus.PAID);
        record.setPaymentConfirmedAt(LocalDateTime.now());
        record.setPaymentConfirmationSource(source);
        
        repository.save(record);
        
        log.info("Payment confirmed for student: {}, transactionId: {}", 
            record.getStudentName(), transactionId);
        
        return true;
    }

    /**
     * Tìm kiếm giao dịch theo keyword (dùng khi không tìm thấy transactionId chính xác)
     * 
     * @param keyword Từ khóa tìm kiếm trong content hoặc transactionId
     * @return Danh sách giao dịch khớp
     */
    @Transactional(readOnly = true)
    public java.util.List<FeeCollectionRecord> searchByKeyword(String keyword) {
        log.info("Searching payment records with keyword: {}", keyword);
        return repository.findByContentOrTransactionIdContaining(keyword);
    }

    /**
     * Kiểm tra trạng thái thanh toán của một giao dịch
     * 
     * @param transactionId Mã giao dịch
     * @return Trạng thái thanh toán
     */
    @Transactional(readOnly = true)
    public String checkPaymentStatus(String transactionId) {
        Optional<FeeCollectionRecord> recordOpt = repository.findByTransactionId(transactionId);
        
        if (recordOpt.isEmpty()) {
            return "NOT_FOUND";
        }
        
        FeeCollectionRecord record = recordOpt.get();
        return record.getPaymentStatus().name();
    }

    /**
     * Lấy danh sách giao dịch chưa thanh toán (để gửi nhắc nhở)
     */
    @Transactional(readOnly = true)
    public java.util.List<FeeCollectionRecord> getUnpaidTransactions() {
        return repository.findByPaymentStatus(FeeCollectionRecord.PaymentStatus.PENDING);
    }

    /**
     * Lấy danh sách giao dịch đã gửi Zalo nhưng chưa thanh toán
     */
    @Transactional(readOnly = true)
    public java.util.List<FeeCollectionRecord> getNotifiedButUnpaid() {
        return repository.findPendingPaymentsWithNotification();
    }
}
