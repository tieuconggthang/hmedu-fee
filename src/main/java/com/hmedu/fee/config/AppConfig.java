package com.hmedu.fee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Data
@Configuration
@ConfigurationProperties(prefix = "hmedu.fee-collection")
public class AppConfig {
    private ExcelConfig excel;
    private VietQRConfig vietqr;
    private ZaloConfig zalo;
    private SchedulerConfig scheduler;
    
    @Data
    public static class ExcelConfig {
        private String filePath;
        private String sheetName;
        private Map<String, Integer> columns;
        private int skipRows;
    }
    
    @Data
    public static class VietQRConfig {
        private boolean enabled;
        private String apiUrl;
        private String defaultBankId;
        private String defaultAccount;
        private String defaultAccountName;
        private String addInfoPrefix;
    }
    
    @Data
    public static class ZaloConfig {
        private boolean enabled;
        private String apiUrl;
        private String messageTemplate;
    }
    
    @Data
    public static class SchedulerConfig {
        private String cron;
    }
    
    @Bean
    public WebClient webClient() {
        return WebClient.builder().build();
    }
}
