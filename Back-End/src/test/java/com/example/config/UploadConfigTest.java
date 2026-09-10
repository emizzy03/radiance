package com.example.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.MultipartConfigElement;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

/**
 * Upload caps are defined in code so every environment (including tests that
 * do not ship a yml override) rejects oversized files instead of buffering
 * them into memory.
 */
class UploadConfigTest {

    @Test
    void multipartLimitsAreFiveAndSixMegabytes() {
        MultipartConfigElement config = new UploadConfig().multipartConfigElement();

        assertThat(config.getMaxFileSize()).isEqualTo(DataSize.ofMegabytes(5).toBytes());
        assertThat(config.getMaxRequestSize()).isEqualTo(DataSize.ofMegabytes(6).toBytes());
    }
}
