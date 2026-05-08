package com.hmedu.fee;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties
public class FeeCollectionApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(FeeCollectionApplication.class, args);
    }
}
