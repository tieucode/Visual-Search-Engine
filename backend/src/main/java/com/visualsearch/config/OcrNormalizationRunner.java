package com.visualsearch.config;

import com.visualsearch.service.OcrNormalizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OcrNormalizationRunner {

    private final OcrNormalizationService normalizationService;

    @Bean
    CommandLineRunner normalizeOcrData() {
        return args -> {
            int updated = normalizationService.normalizeAll();
            System.out.println(
                "Đã chuẩn hóa " + updated + " bản ghi OCR."
            );
        };
    }
}