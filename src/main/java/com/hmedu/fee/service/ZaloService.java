package com.hmedu.fee.service;

import com.hmedu.fee.config.ZaloConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ZaloService {
    
    private final ZaloConfig config;
    private final WebClient.Builder webClientBuilder;
    
    /**
     * Gửi tin nhắn Zalo theo số điện thoại
     */
    public boolean sendMessage(String phone, String message) {
        try {
            String url = config.getApiUrl() + "/send";
            
            WebClient webClient = webClientBuilder.build();
            
            Map<String, Object> requestBody = Map.of(
                "phone", phone,
                "message", message
            );
            
            log.info("Sending Zalo message to: {}", phone);
            
            var response = webClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Zalo message sent successfully to: {}", phone);
                return true;
            } else {
                String error = response != null ? (String) response.get("message") : "Unknown error";
                log.error("Failed to send Zalo message: {}", error);
                return false;
            }
            
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
            
            WebClient webClient = webClientBuilder.build();
            
            var response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null) {
                Boolean loggedIn = (Boolean) response.get("logged_in");
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
     * Gửi ảnh kèm caption qua Zalo
     * Sử dụng OpenZCA CLI: msg image <threadId> -u <url> -m <caption>
     */
    public boolean sendImage(String phone, String imageUrl, String caption) {
        try {
            // 1. Tìm user ID từ phone
            String userId = findUserIdByPhone(phone);
            if (userId == null) {
                log.error("User not found for phone: {}", phone);
                return false;
            }
            
            log.info("Sending image to userId: {} - Phone: {}", userId, phone);
            
            // 2. Dùng OpenZCA CLI để gửi ảnh
            ProcessBuilder pb = new ProcessBuilder(
                "openzca", "msg", "image", userId,
                "-u", imageUrl,
                "-m", caption
            );
            
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // Đọc output
            java.io.BufferedReader reader = new java.io.BufferedReader(
                new java.io.InputStreamReader(process.getInputStream())
            );
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                log.info("Image sent successfully to: {}", phone);
                return true;
            } else {
                log.error("Failed to send image: {}", output);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error sending image to {}", phone, e);
            return false;
        }
    }
    
    /**
     * Tìm user ID từ phone number qua API /friends
     */
    private String findUserIdByPhone(String phone) {
        try {
            String url = config.getApiUrl() + "/friends";
            WebClient webClient = webClientBuilder.build();
            
            var response = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToMono(java.util.Map.class)
                .block();
            
            if (response != null && response.get("friends") != null) {
                java.util.List<java.util.Map<String, Object>> friends = 
                    (java.util.List<java.util.Map<String, Object>>) response.get("friends");
                
                String phoneClean = phone.replace("+", "");
                
                for (java.util.Map<String, Object> friend : friends) {
                    String friendPhone = String.valueOf(friend.get("phoneNumber") == null ? "" : friend.get("phoneNumber"));
                    String friendId = String.valueOf(friend.get("id"));
                    
                    if (friendPhone.contains(phoneClean)) {
                        log.debug("Found userId {} for phone {}", friendId, phone);
                        return friendId;
                    }
                }
            }
            return null;
            
        } catch (Exception e) {
            log.error("Error finding user by phone: {}", phone, e);
            return null;
        }
    }
}
