package com.hmedu.fee.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "hmedu.fee-collection.vietqr")
public class VietQRConfig {
    private boolean enabled;
    private String apiUrl;
    private String defaultBankId;
    private String defaultAccount;
    private String defaultAccountName;
    private String addInfoPrefix;
}
