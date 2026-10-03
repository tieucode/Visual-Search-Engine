package com.visualsearch.service;

import com.visualsearch.dto.upload.ImageUploadRequest;
import com.visualsearch.dto.upload.ImageUploadData;
import com.visualsearch.entity.BatchIndex;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.User;
import com.visualsearch.enums.BatchStatus;
import com.visualsearch.enums.ImageFormat;
import com.visualsearch.enums.IndexStatus;
import com.visualsearch.event.ImageIndexingMessage;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.repository.ImageIndexRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private static final int MAX_INDEXING_MESSAGE_IMAGES = 20;

    private final MinioStorageService minioStorageService;
    private final BatchService batchService;
    private final IndexingJobPersistenceService indexingJobPersistenceService;
    private final IndexingMessagePublisher indexingMessagePublisher;
    private final ImageIndexRepository imageIndexRepository;

    @Qualifier("uploadExecutor")
    private final Executor uploadExecutor;

    @Value("${app.upload.max-file-size-mb:10}")
    private long maxFileSizeMb;

    @PostConstruct
    public void registerImageReaders() {
        ImageIO.scanForPlugins();
    }

    // Điều phối xử lý upload một chunk ảnh (tối đa 50 ảnh).
    public ImageUploadData processUploadBatch(ImageUploadRequest request, User currentUser) {
        // 1. Kiểm tra BatchIndex
        BatchIndex batch = batchService.getBatchEntity(request.getBatchId(), currentUser);
        if (batch.getStatus() != BatchStatus.UPLOADING) {
            throw new BadRequestException("Batch is not accepting uploads. Current status: " + batch.getStatus());
        }

        List<MultipartFile> files = request.getFiles();
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("No files provided for upload");
        }
        if (files.size() > 50) {
            throw new BadRequestException("A maximum of 50 images can be uploaded per request");
        }

        int savedImages = imageIndexRepository.countByBatchId(batch.getId());
        int indexedFailures = imageIndexRepository.countByBatchIdAndStatus(batch.getId(), IndexStatus.FAILED);
        int uploadFailures = Math.max(0, batch.getFailedCount() - indexedFailures);
        if (savedImages + uploadFailures + files.size() > batch.getTotalImages()) {
            throw new BadRequestException("Upload exceeds the number of images declared for this batch");
        }

        long maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;

        // 2. Chạy upload song song từng ảnh bằng uploadExecutor (Async)
        List<CompletableFuture<SingleImageResult>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(
                        () -> processSingleFile(file, batch.getId(), currentUser.getId(), maxFileSizeBytes),
                        uploadExecutor))
                .toList();

        // 3. Đợi toàn bộ các ảnh trong chunk hoàn thành
        List<ImageIndexingMessage.ImageItem> successItems = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (CompletableFuture<SingleImageResult> future : futures) {
            try {
                SingleImageResult result = future.get();
                if (result.isSuccess()) {
                    successItems.add(result.getItem());
                } else {
                    errors.add(result.getErrorMessage());
                }
            } catch (Exception e) {
                log.error("Error retrieving upload result: {}", e.getMessage());
                errors.add("Internal processing error: " + e.getMessage());
            }
        }

        // The worker accepts at most 20 images per message, even when an HTTP chunk has 50.
        if (!successItems.isEmpty()) {
            for (int start = 0; start < successItems.size(); start += MAX_INDEXING_MESSAGE_IMAGES) {
                List<ImageIndexingMessage.ImageItem> items = List.copyOf(successItems.subList(
                        start, Math.min(start + MAX_INDEXING_MESSAGE_IMAGES, successItems.size())));
                indexingMessagePublisher.publish(new ImageIndexingMessage(items));
            }
            log.info("Dispatched indexing messages for batch {} with {} images", batch.getId(), successItems.size());
        }

        if (!errors.isEmpty()) {
            batchService.recordUploadFailures(batch.getId(), errors.size());
        }

        // 5. Nếu là lượt gửi cuối cùng (isLast = true), chốt sổ batch chuyển sang
        // PROCESSING
        if (request.isLast()) {
            batchService.completeBatch(batch.getId());
        }

        // 6. Lấy lại batch để trả về trạng thái mới nhất
        BatchIndex updatedBatch = batchService.getBatchEntity(batch.getId(), currentUser);

        return ImageUploadData.builder()
                .batchId(batch.getId())
                .uploadedCount(successItems.size())
                .failedCount(errors.size())
                .batchStatus(updatedBatch.getStatus())
                .errors(errors)
                .build();
    }

    // Xử lý độc lập từng file ảnh (validate -> MinIO -> Thumbnail -> PostgreSQL).
    private SingleImageResult processSingleFile(
            MultipartFile file,
            UUID batchId,
            UUID userId,
            long maxFileSizeBytes) {
        if (file == null || file.isEmpty()) {
            return SingleImageResult.failure("Empty image file is not allowed");
        }
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String extension = getFileExtension(originalFilename);

        // Validate format trực tiếp dựa vào enum ImageFormat
        ImageFormat imageFormat = parseImageFormat(extension);
        if (imageFormat == null) {
            return SingleImageResult.failure(originalFilename + ": Unsupported format '" + extension
                    + "'. Allowed formats: JPEG, JPG, PNG, WEBP");
        }

        // Validate size
        if (file.getSize() > maxFileSizeBytes) {
            return SingleImageResult.failure(originalFilename + ": File size (" + (file.getSize() / 1024 / 1024)
                    + "MB) exceeds limit of " + maxFileSizeMb + "MB");
        }

        String originalObjectName = null;
        String thumbObjectName = null;
        boolean persisted = false;
        try {
            validateImageContent(file, imageFormat);

            // Đọc kích thước ảnh width x height
            int width;
            int height;
            try (InputStream is = file.getInputStream()) {
                BufferedImage bi = ImageIO.read(is);
                if (bi == null) {
                    throw new BadRequestException("Image cannot be decoded");
                }
                width = bi.getWidth();
                height = bi.getHeight();
            } catch (IOException e) {
                throw new BadRequestException("Image cannot be decoded");
            }

            // Đặt tên file ngẫu nhiên trên MinIO
            String fileId = UUID.randomUUID().toString();
            originalObjectName = fileId + "." + extension.toLowerCase(Locale.ROOT);
            thumbObjectName = fileId + "_thumb.jpg";

            // Upload ảnh gốc lên MinIO
            minioStorageService.uploadImage(file, originalObjectName);

            // Sinh thumbnail và upload lên MinIO
            try {
                minioStorageService.uploadThumbnail(file, thumbObjectName);
            } catch (Exception e) {
                log.warn("Thumbnail generation failed for {}: {}", originalFilename, e.getMessage());
                thumbObjectName = null;
            }

            String fullImageUrl = minioStorageService.getImageUrl(originalObjectName);

            // Lưu Image và trạng thái indexing ban đầu trong cùng transaction.
            Image image = indexingJobPersistenceService.createImageAndPendingIndex(
                    batchId,
                    userId,
                    originalObjectName,
                    thumbObjectName,
                    width,
                    height,
                    file.getSize(),
                    imageFormat);
            persisted = true;

            ImageIndexingMessage.ImageItem item = new ImageIndexingMessage.ImageItem(image.getId(), fullImageUrl);

            return SingleImageResult.success(item);

        } catch (Exception e) {
            if (!persisted) {
                if (originalObjectName != null) {
                    minioStorageService.deleteImage(originalObjectName);
                }
                if (thumbObjectName != null) {
                    minioStorageService.deleteThumbnail(thumbObjectName);
                }
            }
            log.error("Failed to process image '{}': {}", originalFilename, e.getMessage(), e);
            return SingleImageResult.failure(originalFilename + ": " + e.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1);
        }
        return "";
    }

    private ImageFormat parseImageFormat(String extension) {
        if (extension == null || extension.isBlank()) {
            return null;
        }
        try {
            ImageFormat format = ImageFormat.valueOf(extension.toUpperCase(Locale.ROOT));
            return switch (format) {
                case JPEG, JPG, PNG, WEBP -> format;
                default -> null;
            };
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void validateImageContent(MultipartFile file, ImageFormat format) throws Exception {
        String contentType = file.getContentType();
        boolean matchingType = switch (format) {
            case JPEG, JPG -> "image/jpeg".equalsIgnoreCase(contentType)
                    || "image/jpg".equalsIgnoreCase(contentType);
            case PNG -> "image/png".equalsIgnoreCase(contentType);
            case WEBP -> "image/webp".equalsIgnoreCase(contentType);
            default -> false;
        };
        if (!matchingType) {
            throw new BadRequestException("File content type does not match its extension");
        }

        byte[] header;
        try (InputStream input = file.getInputStream()) {
            header = input.readNBytes(12);
        }
        boolean matchingHeader = switch (format) {
            case JPEG, JPG -> header.length >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
            case PNG -> header.length >= 8
                    && (header[0] & 0xff) == 0x89 && header[1] == 'P'
                    && header[2] == 'N' && header[3] == 'G'
                    && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10;
            case WEBP -> header.length >= 12
                    && header[0] == 'R' && header[1] == 'I' && header[2] == 'F' && header[3] == 'F'
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
        if (!matchingHeader) {
            throw new BadRequestException("File contents are not a supported image format");
        }
    }

    private static class SingleImageResult {
        private final boolean success;
        private final ImageIndexingMessage.ImageItem item;
        private final String errorMessage;

        private SingleImageResult(boolean success, ImageIndexingMessage.ImageItem item, String errorMessage) {
            this.success = success;
            this.item = item;
            this.errorMessage = errorMessage;
        }

        public static SingleImageResult success(ImageIndexingMessage.ImageItem item) {
            return new SingleImageResult(true, item, null);
        }

        public static SingleImageResult failure(String errorMessage) {
            return new SingleImageResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public ImageIndexingMessage.ImageItem getItem() {
            return item;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
