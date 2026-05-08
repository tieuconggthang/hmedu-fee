package com.hmedu.fee.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fee_collection_records")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeeCollectionRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "student_name", nullable = false)
    private String studentName;
    
    @Column(name = "phone_number")
    private String phoneNumber;
    
    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;
    
    @Column(name = "content", length = 500)
    private String content;
    
    @Column(name = "account_name")
    private String accountName;
    
    @Column(name = "account_number")
    private String accountNumber;
    
    @Column(name = "bank_name")
    private String bankName;
    
    @Column(name = "bank_id")
    private String bankId;
    
    @Column(name = "qr_code_url", length = 2000)
    private String qrCodeUrl;
    
    @Column(name = "zalo_message_sent")
    private Boolean zaloMessageSent = false;
    
    @Column(name = "zalo_message_id")
    private String zaloMessageId;
    
    @Column(name = "zalo_sent_at")
    private LocalDateTime zaloSentAt;
    
    @Column(name = "zalo_error_message", length = 1000)
    private String zaloErrorMessage;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status")
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Column(name = "payment_confirmed_at")
    private LocalDateTime paymentConfirmedAt;
    
    @Column(name = "payment_confirmation_source")
    private String paymentConfirmationSource;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "month_year")
    private String monthYear;
    
    @Column(name = "transaction_id", unique = true, length = 200)
    private String transactionId;  // Mã định danh giao dịch (từ cột G - nội dung CK)
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    public enum PaymentStatus {
        PENDING,           // Chờ thanh toán
        NOTIFIED,          // Đã gửi thông báo
        PAID,              // Đã thanh toán (confirm qua email)
        OVERDUE,           // Quá hạn
        CANCELLED          // Đã hủy
    }
}
