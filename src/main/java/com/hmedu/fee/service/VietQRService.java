package com.hmedu.fee.service;

import com.hmedu.fee.config.VietQRConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class VietQRService {
    
    private final VietQRConfig config;
    private final WebClient webClient;
    
    /**
     * Tạo URL VietQR động - Chỉ dùng amount và addInfo (KHÔNG accountName để tránh lỗi)
     */
    public String generateDynamicQRUrl(String bankId, String accountNumber, 
                                       String accountName, BigDecimal amount, 
                                       String description) {
        try {
            // QUAN TRỌNG: Chỉ dùng amount + addInfo, KHÔNG thêm accountName
            // accountName đã có trong QR data, không cần thêm vào URL
            
            // Encode - dùng %20 thay vì + cho dấu cách
            String encodedDesc = URLEncoder.encode(description, StandardCharsets.UTF_8)
                .replace("+", "%20");
            String amountStr = amount.stripTrailingZeros().toPlainString();
            
            // Sử dụng img.vietqr.io với đầy đủ thông tin
            String qrUrl = String.format(
                "https://img.vietqr.io/image/%s-%s-qr_only.png?amount=%s&addInfo=%s",
                bankId, accountNumber, amountStr, encodedDesc
            );
            
            log.info("Generated VietQR URL: {}", qrUrl);
            log.info("  - Bank ID: {}, Account: {}, Amount: {}, Desc: {}", bankId, accountNumber, amountStr, description);
            return qrUrl;
            
        } catch (Exception e) {
            log.error("Error generating VietQR URL", e);
            // Fallback
            return String.format(
                "https://img.vietqr.io/image/%s-%s-qr_only.png?amount=%s",
                bankId, accountNumber, amount
            );
        }
    }
    
    /**
     * Lấy mã ngân hàng VietQR từ tên ngân hàng
     */
    public String getBankId(String bankName) {
        if (bankName == null || bankName.trim().isEmpty()) {
            return config.getDefaultBankId(); // Default từ config
        }
        
        String normalized = bankName.toUpperCase().trim();
        
        // Mapping tên ngân hàng → mã VietQR
        return switch (normalized) {
            case "TECHCOMBANK", "TCB", "TECHCOM BANK" -> "970407";
            case "VPBANK", "VP BANK", "NGAN HANG TMCP VUOT TROI VIET" -> "970432";
            case "VIETCOMBANK", "VCB", "VIETCOM BANK" -> "970436";
            case "BIDV", "BIDV BANK" -> "970418";
            case "AGRIBANK", "AGRI BANK" -> "970405";
            case "MBBANK", "MB", "MB BANK", "NGAN HANG TMCP QUAN DOI" -> "970422";
            case "ACB", "ASIA COMMERCIAL BANK" -> "970416";
            case "TPBANK", "TP BANK", "TIEN PHONG BANK" -> "970423";
            case "SACOMBANK", "SACOM BANK" -> "970403";
            case "VIETINBANK", "VIETIN BANK", "CBBANK" -> "970415";
            case "MSB", "MARITIME BANK", "NGAN HANG HANG HAI" -> "970426";
            case "OCB", "ORIENTAL BANK" -> "970448";
            case "SHB", "SAIGON HANOI BANK" -> "970443";
            case "VIB", "VIB BANK", "NGAN HANG QUOC TE" -> "970441";
            case "PVCOMBANK", "PVCOM BANK" -> "970430";
            case "HDBANK", "HD BANK", "HOA PHAT BANK" -> "970437";
            case "NAMABANK", "NAMA BANK" -> "970428";
            default -> config.getDefaultBankId(); // Nếu không nhận diện được, dùng default
        };
    }
}
