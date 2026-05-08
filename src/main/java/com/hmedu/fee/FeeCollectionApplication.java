package com.hmedu.fee;

import com.hmedu.fee.service.FeeCollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
@RequiredArgsConstructor
public class FeeCollectionApplication {
    
    private final FeeCollectionService feeCollectionService;
    
    public static void main(String[] args) {
        SpringApplication.run(FeeCollectionApplication.class, args);
    }
    
    /**
     * Chạy test ngay lập tức khi dùng profile "manual"
     */
    @Bean
    @Profile("manual")
    public CommandLineRunner runManual() {
        return args -> {
            log.info("🚀 Chạy chế độ MANUAL - Gửi tin nhắn ngay lập tức");
            feeCollectionService.triggerManually();
            log.info("✅ Hoàn thành! Thoát...");
            System.exit(0);
        };
    }
}
