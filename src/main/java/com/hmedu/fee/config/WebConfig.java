package com.hmedu.fee.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cấu hình phục vụ file tĩnh (images) từ thư mục /app/data/images
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path imagesDir = Paths.get("/app/data/images");
        String imagesPath = imagesDir.toUri().toString();

        registry.addResourceHandler("/images/**")
                .addResourceLocations(imagesPath)
                .setCachePeriod(3600);
    }
}
