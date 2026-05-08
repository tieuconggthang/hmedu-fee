package com.hmedu.fee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hmedu.fee-collection.zalo")
public class ZaloConfig {
    private boolean enabled;
    private String apiUrl;
    private String messageTemplate;
}
