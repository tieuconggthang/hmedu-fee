package com.hmedu.fee.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StudentFeeDto {
    private int rowIndex;
    private String studentName;
    private String phone;
    private BigDecimal amount;
    private String content;
    private String accountName;
    private String accountNumber;
    private String bank;
    private String bankId;
    private String qrCodeUrl;
    private String transactionId;  // Mã định danh giao dịch (từ cột G - nội dung CK)
}
