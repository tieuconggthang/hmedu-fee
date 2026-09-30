package com.hmedu.fee.controller;

import com.hmedu.fee.dto.StudentFeeDto;
import com.hmedu.fee.service.FeeCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/fee")
@RequiredArgsConstructor
public class FeeController {

    private final FeeCollectionService feeCollectionService;

    @Value("${api.secret-key:}")
    private String secretKey;

    private void validateApiKey(String providedKey) {
        if (secretKey != null && !secretKey.isEmpty()) {
            if (providedKey == null || !providedKey.equals(secretKey)) {
                throw new SecurityException("Invalid or missing API key");
            }
        }
    }

    /**
     * API echo để test body từ Windows
     */
    @PostMapping("/echo")
    public ResponseEntity<String> echo(@RequestBody String body) {
        log.info("ECHO received body length={} content={}", body.length(), body);
        return ResponseEntity.ok("Received: [" + body + "]");
    }

    /**
     * API gửi thông báo học phí - nhận form/query params (tránh lỗi JSON từ Windows/Git Bash)
     */
    @PostMapping("/send-form")
    public ResponseEntity<Map<String, Object>> sendFeeForm(
            @RequestHeader(value = "X-API-Key", required = false) String headerKey,
            @RequestParam(required = false) String apiKey,
            @RequestParam String studentName,
            @RequestParam String phone,
            @RequestParam String amount,
            @RequestParam(required = false, defaultValue = "") String content,
            @RequestParam(required = false, defaultValue = "") String accountNumber,
            @RequestParam(required = false, defaultValue = "") String bank,
            @RequestParam(required = false, defaultValue = "") String bankId,
            @RequestParam(required = false, defaultValue = "") String transactionId
    ) {
        String keyToCheck = (headerKey != null && !headerKey.isEmpty()) ? headerKey : apiKey;
        try {
            validateApiKey(keyToCheck);
        } catch (SecurityException e) {
            log.warn("Unauthorized access to /send-form: invalid API key");
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Unauthorized: " + e.getMessage()
            ));
        }

        log.info("API /api/fee/send-form called: studentName={}, phone={}, amount={}", studentName, phone, amount);

        try {
            StudentFeeDto dto = StudentFeeDto.builder()
                .studentName(studentName)
                .phone(phone)
                .amount(new java.math.BigDecimal(amount))
                .content(content)
                .accountNumber(accountNumber.isEmpty() ? null : accountNumber)
                .bank(bank.isEmpty() ? null : bank)
                .bankId(bankId.isEmpty() ? null : bankId)
                .build();

            if (transactionId == null || transactionId.isEmpty()) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
                dto.setTransactionId(ts + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            } else {
                dto.setTransactionId(transactionId);
            }

            feeCollectionService.processStudent(dto);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Fee notification sent successfully",
                "student", dto.getStudentName(),
                "transactionId", dto.getTransactionId(),
                "qrCodeUrl", dto.getQrCodeUrl()
            ));

        } catch (Exception e) {
            log.error("Error in send-form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed: " + e.getMessage()
            ));
        }
    }

    /**
     * API gửi thông báo học phí cho 1 học sinh (JSON body).
     * Nhận raw text để xử lý lỗi quote từ Windows/Git Bash
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendFeeNotification(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody String body) {
        try {
            validateApiKey(apiKey);
        } catch (SecurityException e) {
            log.warn("Unauthorized access to /send: invalid API key");
            return ResponseEntity.status(401).body(Map.of(
                "success", false,
                "message", "Unauthorized: " + e.getMessage()
            ));
        }

        log.info("API /api/fee/send raw body: {}", body);

        try {
            // Clean nếu Git Bash gửi backslash-escaped JSON
            String cleaned = body.trim();
            if (cleaned.startsWith("\\")) {
                cleaned = cleaned.replace("\\{", "{").replace("\\}", "}")
                    .replace("\\\"", "\"").replace("\\\\", "\\");
            }

            // Parse JSON
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> request = mapper.readValue(cleaned, Map.class);
            log.info("Parsed JSON: {}", request);

            StudentFeeDto dto = StudentFeeDto.builder()
                .studentName((String) request.get("studentName"))
                .phone((String) request.get("phone"))
                .amount(request.get("amount") != null ? new java.math.BigDecimal(request.get("amount").toString()) : null)
                .content((String) request.get("content"))
                .accountName((String) request.get("accountName"))
                .accountNumber((String) request.get("accountNumber"))
                .bank((String) request.get("bank"))
                .bankId((String) request.get("bankId"))
                .build();

            if (request.get("transactionId") == null) {
                String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
                dto.setTransactionId(ts + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            } else {
                dto.setTransactionId((String) request.get("transactionId"));
            }

            feeCollectionService.processStudent(dto);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Fee notification sent successfully",
                "student", dto.getStudentName(),
                "transactionId", dto.getTransactionId(),
                "qrCodeUrl", dto.getQrCodeUrl()
            ));

        } catch (Exception e) {
            log.error("Error parsing body: [{}] - {}", body, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to parse JSON: " + e.getMessage(),
                "rawBody", body
            ));
        }
    }
}
