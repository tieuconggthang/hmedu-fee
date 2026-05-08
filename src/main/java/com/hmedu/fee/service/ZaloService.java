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
     * Gửi ảnh kèm caption qua Zalo API
     * Sử dụng HTTP POST /send-image (query params)
     */
    public boolean sendImage(String phone, String imageUrl, String caption) {
        try {
            log.info("Sending image to phone: {}", phone);
            
            // Encode caption để truyền qua URL
            String encodedCaption = java.net.URLEncoder.encode(caption, java.nio.charset.StandardCharsets.UTF_8);
            
            // Gọi API /send-image với query params
            String url = String.format("%s/send-image?phone=%s&image_url=%s&caption=%s",
                config.getApiUrl(),
                phone,
                java.net.URLEncoder.encode(imageUrl, java.nio.charset.StandardCharsets.UTF_8),
                encodedCaption
            );
            
            WebClient webClient = webClientBuilder.build();
            
            var response = webClient.post()
                .uri(url)
                .retrieve()
                .bodyToMono(Map.class)
                .block();
            
            if (response != null && Boolean.TRUE.equals(response.get("success"))) {
                log.info("Image sent successfully to: {}", phone);
                return true;
            } else {
                String error = response != null ? (String) response.get("message") : "Unknown error";
                log.error("Failed to send image: {}", error);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Error sending image to {}", phone, e);
            return false;
        }
    }
}
