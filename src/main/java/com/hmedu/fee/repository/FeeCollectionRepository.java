package com.hmedu.fee.repository;

import com.hmedu.fee.entity.FeeCollectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FeeCollectionRepository extends JpaRepository<FeeCollectionRecord, Long> {
    
    Optional<FeeCollectionRecord> findByPhoneNumberAndMonthYear(String phoneNumber, String monthYear);
    
    List<FeeCollectionRecord> findByMonthYear(String monthYear);
    
    List<FeeCollectionRecord> findByPaymentStatus(FeeCollectionRecord.PaymentStatus status);
    
    List<FeeCollectionRecord> findByZaloMessageSentFalse();
    
    @Query("SELECT f FROM FeeCollectionRecord f WHERE f.paymentStatus = 'PENDING' AND f.zaloMessageSent = true")
    List<FeeCollectionRecord> findPendingPaymentsWithNotification();
    
    @Query("SELECT f FROM FeeCollectionRecord f WHERE f.paymentStatus = 'PAID' AND f.paymentConfirmedAt IS NULL")
    List<FeeCollectionRecord> findPaidButNotConfirmed();
    
    // Tìm theo mã giao dịch (để match với thông báo từ ngân hàng)
    Optional<FeeCollectionRecord> findByTransactionId(String transactionId);
    
    // Tìm theo nội dung chuyển khoản (nếu ngân hàng trả về nội dung khác transactionId)
    @Query("SELECT f FROM FeeCollectionRecord f WHERE f.content LIKE %:keyword% OR f.transactionId LIKE %:keyword%")
    List<FeeCollectionRecord> findByContentOrTransactionIdContaining(String keyword);
}
