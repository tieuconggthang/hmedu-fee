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
     * Tạo URL VietQR động
     */
    public String generateDynamicQRUrl(String bankId, String accountNumber, 
                                       String accountName, BigDecimal amount, 
                                       String description) {
        try {
            // Format: https://img.vietqr.io/image/BANK_ID-ACCOUNT_NUMBER-qr_only.png
            // Hoặc dùng API generate
            
            String encodedDesc = URLEncoder.encode(description, StandardCharsets.UTF_8);
            
            // Sử dụng VietQR API
            String apiUrl = String.format(
                "%s?accountNo=%s&accountName=%s&acqId=%s&amount=%s&addInfo=%s",
                config.getApiUrl(),
                accountNumber,
                URLEncoder.encode(accountName, StandardCharsets.UTF_8),
                bankId,
                amount.toString(),
                encodedDesc
            );
            
            log.info("Generated VietQR URL: {}", apiUrl);
            return apiUrl;
            
        } catch (Exception e) {
            log.error("Error generating VietQR URL", e);
            // Fallback: trả về URL dạng image
            return String.format(
                "https://img.vietqr.io/image/%s-%s-qr_only.png?amount=%s&addInfo=%s",
                bankId, accountNumber, amount, 
                URLEncoder.encode(description, StandardCharsets.UTF_8)
            );
        }
    }
    
    /**
     * Lấy mã ngân hàng từ tên
     */
    public String getBankId(String bankName) {
        // Mapping tên ngân hàng -> mã VietQR
        return switch (bankName.toUpperCase()) {
            case "TECHCOMBANK", "TCB" -> "970407";
            case "VIETCOMBANK", "VCB" -> "970436";
            case "BIDV" -> "970418";
            case "AGRIBANK" -> "970405";
            case "MBBANK", "MB" -> "970422";
            case "ACB" -> "970416";
            case "VPBANK" -> "970432";
            case "TPBANK" -> "970423";
            default -> config.getDefaultBankId(); // Default Techcombank
        };
    }
}
