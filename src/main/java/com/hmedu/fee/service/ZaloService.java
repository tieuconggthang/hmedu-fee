package com.hmedu.fee.service;

import com.hmedu.fee.config.ZaloConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloService {
    
    private final ZaloConfig config;
    private final RestTemplate restTemplate;
    
    /**
     * Gửi tin nhắn Zalo theo số điện thoại
     */
    public boolean sendMessage(String phone, String message) {
        try {
            String url = config.getApiUrl() + "/send";
            
            Map<String, Object> requestBody = Map.of(
                "phone", phone,
                "message", message
            );
            
            log.info("Sending Zalo message to: {}", phone);
            
            ResponseEntity<Map> response = restTemplate.postForEntity(
                url, 
                new org.springframework.http.HttpEntity<>(requestBody),
                Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Boolean success = (Boolean) response.getBody().get("success");
                if (Boolean.TRUE.equals(success)) {
                    log.info("Zalo message sent successfully to: {}", phone);
                    return true;
                }
            }
            
            log.error("Failed to send Zalo message: {}", response);
            return false;
            
        } catch (Exception e) {
            log.error("Error sending Zalo message to {}", phone, e);
            return false;
        }
    }
    
    /**
     * Kiểm tra trạng thái đăng nhập Zalo API
     */
    public boolean checkLoginStatus() {
        try {
            String url = config.getApiUrl() + "/login/status";
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null) {
                Boolean loggedIn = (Boolean) response.getBody().get("logged_in");
                return Boolean.TRUE.equals(loggedIn);
            }
            return false;
            
        } catch (Exception e) {
            log.error("Error checking Zalo login status", e);
            return false;
        }
    }
    
    /**
     * Format message từ template
     */
    public String formatMessage(String template, Map<String, String> params) {
        String message = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return message;
    }
    
    /**
     * Gửi ảnh dạng base64 qua Zalo API
     */
    public boolean sendImageBase64(String phone, String imageBase64, String caption) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[Attempt {}/{}] Sending base64 image to phone: {}", attempt, maxRetries, phone);

                if (attempt > 1) {
                    Thread.sleep(2000);
                }

                String url = config.getApiUrl() + "/send-image-base64";

                Map<String, Object> requestBody = Map.of(
                    "phone", phone,
                    "image_base64", imageBase64,
                    "caption", caption
                );

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Boolean success = (Boolean) response.getBody().get("success");
                    if (Boolean.TRUE.equals(success)) {
                        log.info("Base64 image sent successfully to: {} (attempt {})", phone, attempt);
                        return true;
                    } else {
                        String error = (String) response.getBody().get("message");
                        log.warn("Attempt {} failed: {}", attempt, error);
                        if (attempt == maxRetries) {
                            log.error("Failed after {} attempts: {}", maxRetries, error);
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Attempt {} error: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Error after {} attempts", maxRetries, e);
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * Gửi ảnh kèm caption qua Zalo API - Dùng RestTemplate thay vì WebClient
     */
    public boolean sendImage(String phone, String imageUrl, String caption) {
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[Attempt {}/{}] Sending image to phone: {}", attempt, maxRetries, phone);
                
                if (attempt > 1) {
                    Thread.sleep(2000);
                }
                
                // Dùng POST /send-image-json với JSON body (tránh lỗi URL encoding, gửi bằng phone)
                String url = config.getApiUrl() + "/send-image-json";
                
                Map<String, Object> requestBody = Map.of(
                    "phone", phone,
                    "image_url", imageUrl,  // URL gốc, OpenZCA sẽ decode nếu cần
                    "caption", caption
                );
                
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                
                // LOG để test
                log.info("========================================");
                log.info("POST /send-image-json");
                log.info("Request body: {}", requestBody);
                log.info("========================================");
                
                ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
                );
                
                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    Boolean success = (Boolean) response.getBody().get("success");
                    if (Boolean.TRUE.equals(success)) {
                        log.info("Image sent successfully to: {} (attempt {})", phone, attempt);
                        return true;
                    } else {
                        String error = (String) response.getBody().get("message");
                        log.warn("Attempt {} failed: {}", attempt, error);
                        if (attempt == maxRetries) {
                            log.error("Failed after {} attempts: {}", maxRetries, error);
                            return false;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Attempt {} error: {}", attempt, e.getMessage());
                if (attempt == maxRetries) {
                    log.error("Error after {} attempts", maxRetries, e);
                    return false;
                }
            }
        }
        return false;
    }
    
    /**
     * Lookup user_id từ số điện thoại qua /friends
     */
    private String lookupUserIdByPhone(String phone) {
        try {
            String url = config.getApiUrl() + "/friends";
            
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            
            if (response.getBody() != null && response.getBody().containsKey("friends")) {
                java.util.List<Map<String, Object>> friends = (java.util.List<Map<String, Object>>) response.getBody().get("friends");
                
                for (Map<String, Object> friend : friends) {
                    String friendPhone = (String) friend.get("phoneNumber");
                    if (phone.equals(friendPhone)) {
                        return (String) friend.get("userId");
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Error looking up user_id for phone: {}", phone, e);
            return null;
        }
    }
}
