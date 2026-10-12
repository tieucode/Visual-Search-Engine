package com.visualsearch.service;

import com.visualsearch.entity.ImageOcr;
import com.visualsearch.repository.ImageOcrRepository;
import com.visualsearch.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OcrNormalizationService {

    private final ImageOcrRepository imageOcrRepository;

    /**
     * Chuẩn hóa lại toàn bộ dữ liệu OCR đã lưu trong database.
     */
    @Transactional
    public int normalizeAll() {
        var records = imageOcrRepository.findAll();

        int updated = 0;

        for (ImageOcr record : records) {
            String rawText = record.getRawText();

            if (rawText == null || rawText.isBlank()) {
                continue;
            }

            String normalizedText = TextNormalizer.normalize(rawText);

            if (!normalizedText.equals(record.getNormalizedText())) {
                record.setNormalizedText(normalizedText);
                updated++;
            }
        }

        imageOcrRepository.saveAll(records);

        return updated;
    }
}