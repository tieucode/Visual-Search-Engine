package com.visualsearch.service;

import com.visualsearch.dto.history.ImageUploadHistoryResponse;
import com.visualsearch.entity.Image;
import com.visualsearch.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImageHistoryService {

    private final ImageRepository imageRepository;
    private final MinioStorageService minioStorageService;

    @Transactional(readOnly = true)
    public Page<ImageUploadHistoryResponse> getMyUploads(
            UUID userId,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Số trang không được nhỏ hơn 0"
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Kích thước trang phải từ 1 đến 100"
            );
        }

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        return imageRepository
                .findByUploadedByIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::toResponse);
    }

    private ImageUploadHistoryResponse toResponse(Image image) {
        String imageUrl = minioStorageService.getImageUrl(
                image.getPath()
        );

        String thumbnailUrl = image.getThumbnailPath() == null
                ? null
                : minioStorageService.getImageUrl(
                        image.getThumbnailPath()
                );

        return new ImageUploadHistoryResponse(
                image.getId(),
                imageUrl,
                thumbnailUrl,
                image.getWidth(),
                image.getHeight(),
                image.getFileSize(),
                image.getFileFormat() == null
                        ? null
                        : image.getFileFormat().name(),
                image.getType() == null
                        ? null
                        : image.getType().name(),
                image.getCreatedAt()
        );
    }
}
