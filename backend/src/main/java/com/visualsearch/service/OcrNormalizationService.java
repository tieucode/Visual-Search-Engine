package com.visualsearch.service;

import com.visualsearch.entity.ImageOcr;
import com.visualsearch.repository.ImageOcrRepository;
import com.visualsearch.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OcrNormalizationService {

    private final ImageOcrRepository imageOcrRepository;

    /**
     * Chuẩn hóa lại toàn bộ dữ liệu OCR đã lưu trong database.
     */
    public int normalizeAll() {
        int updated = 0;
        UUID lastId = null;
        Pageable batch = PageRequest.of(0, 500, Sort.by(Sort.Direction.ASC, "id"));

        while (true) {
            List<ImageOcr> records = lastId == null
                    ? imageOcrRepository.findByIdIsNotNull(batch)
                    : imageOcrRepository.findByIdGreaterThanOrderByIdAsc(lastId, batch);
            if (records.isEmpty()) {
                break;
            }

            lastId = records.get(records.size() - 1).getId();
            List<ImageOcr> changed = new ArrayList<>();
            for (ImageOcr record : records) {
                String rawText = record.getRawText();
                if (rawText == null || rawText.isBlank()) continue;

                String normalizedText = TextNormalizer.normalize(rawText);
                if (!normalizedText.equals(record.getNormalizedText())) {
                    record.setNormalizedText(normalizedText);
                    changed.add(record);
                }
            }
            if (!changed.isEmpty()) {
                imageOcrRepository.saveAll(changed);
                updated += changed.size();
            }
        }

        return updated;
    }
}
