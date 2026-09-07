package com.example.config;

import jakarta.servlet.MultipartConfigElement;

import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Multipart limits for image uploads. Defined in code (rather than only in the
 * env-driven {@code application.yml}) so the caps are enforced in every
 * environment, including tests and deployments that do not ship a yml override.
 * Overriding this bean is possible because Spring Boot's autoconfigured
 * {@code MultipartConfigElement} is {@code @ConditionalOnMissingBean}.
 */
@Configuration
public class UploadConfig {

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofMegabytes(5));
        factory.setMaxRequestSize(DataSize.ofMegabytes(6));
        return factory.createMultipartConfig();
    }
}
