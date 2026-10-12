package com.visualsearch.service;

import com.visualsearch.dto.search.OcrSearchResult;
import com.visualsearch.entity.ImageOcr;
import com.visualsearch.repository.ImageOcrRepository;
import com.visualsearch.util.TextNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OcrSearchService {

    private final ImageOcrRepository imageOcrRepository;
    private final MinioStorageService storage;

    @Transactional(readOnly = true)
    public List<OcrSearchResult> search(String keyword) {
        String normalizedQuery = TextNormalizer.normalize(keyword);

        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        List<ImageOcr> results =
                imageOcrRepository.searchByNormalizedText(normalizedQuery);

        return results.stream()
                .map(ocr -> {
                    var image = ocr.getImage();

                    String imageUrl =
                            storage.getPublicImageUrl(image.getPath());

                    String thumbnailUrl = image.getThumbnailPath() == null
                            || image.getThumbnailPath().isBlank()
                            ? imageUrl
                            : storage.getPresignedThumbnailUrl(
                            image.getThumbnailPath());

                    return new OcrSearchResult(
                            image.getId(),
                            imageUrl,
                            thumbnailUrl,
                            image.getWidth(),
                            image.getHeight(),
                            ocr.getRawText(),
                            ocr.getConfidenceScore()
                    );
                })
                .toList();
    }
}