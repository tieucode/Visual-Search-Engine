package com.visualsearch.startup;

import com.visualsearch.service.OcrNormalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = "app.ocr-normalization", name = "enabled", havingValue = "true")
public class OcrNormalizationRunner implements CommandLineRunner {

    private final OcrNormalizationService normalizationService;

    @Override
    public void run(String... args) {
        int updated = normalizationService.normalizeAll();
        log.info("OCR normalization backfill completed. Updated {} records.", updated);
    }
}
