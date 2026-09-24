package com.visualsearch.service;

import com.visualsearch.dto.upload.ImageUploadRequest;
import com.visualsearch.dto.upload.ImageUploadResponse;
import com.visualsearch.entity.BatchIndex;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.User;
import com.visualsearch.enums.BatchStatus;
import com.visualsearch.enums.ImageFormat;
import com.visualsearch.enums.ImageType;
import com.visualsearch.event.ImageUploadedEvent;
import com.visualsearch.exception.BadRequestException;
import com.visualsearch.repository.ImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final MinioStorageService minioStorageService;
    private final BatchService batchService;
    private final ImageRepository imageRepository;
    private final RabbitTemplate rabbitTemplate;

    @Qualifier("uploadExecutor")
    private final Executor uploadExecutor;

    @Value("${app.rabbitmq.exchange}")
    private String exchangeName;

    @Value("${app.rabbitmq.routing-key.image-uploaded}")
    private String routingKey;

    @Value("${app.upload.max-file-size-mb:20}")
    private long maxFileSizeMb;

    // Điều phối xử lý upload một chunk ảnh (tối đa 20 ảnh).
    public ImageUploadResponse processUploadBatch(ImageUploadRequest request, User currentUser) {
        // 1. Kiểm tra BatchIndex
        BatchIndex batch = batchService.getBatchEntity(request.getBatchId());
        if (batch.getStatus() != BatchStatus.UPLOADING) {
            throw new BadRequestException("Batch is not accepting uploads. Current status: " + batch.getStatus());
        }

        List<MultipartFile> files = request.getFiles();
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("No files provided for upload");
        }
        if (files.size() > 20) {
            throw new BadRequestException("A maximum of 20 images can be uploaded per request");
        }

        long maxFileSizeBytes = maxFileSizeMb * 1024 * 1024;

        // 2. Chạy upload song song từng ảnh bằng uploadExecutor (Async)
        List<CompletableFuture<SingleImageResult>> futures = files.stream()
                .map(file -> CompletableFuture.supplyAsync(
                        () -> processSingleFile(file, currentUser, maxFileSizeBytes),
                        uploadExecutor))
                .toList();

        // 3. Đợi toàn bộ các ảnh trong chunk hoàn thành
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<ImageUploadedEvent.ImageItem> successItems = new ArrayList<>();
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

        // 4. Bắn 1 Message duy nhất vào RabbitMQ cho toàn bộ ảnh thành công
        if (!successItems.isEmpty()) {
            ImageUploadedEvent event = ImageUploadedEvent.builder()
                    .batchId(batch.getId())
                    .totalImages(successItems.size())
                    .images(successItems)
                    .build();

            try {
                rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
                log.info("Dispatched ImageUploadedEvent for batch {} with {} images", batch.getId(),
                        successItems.size());
            } catch (Exception e) {
                log.error("Failed to publish RabbitMQ event for batch {}: {}", batch.getId(), e.getMessage(), e);
            }
        }

        // 5. Nếu là lượt gửi cuối cùng (isLast = true), chốt sổ batch chuyển sang
        // PROCESSING
        if (request.isLast()) {
            batchService.completeBatch(batch);
        }

        // 6. Lấy lại batch để trả về trạng thái mới nhất
        BatchIndex updatedBatch = batchService.getBatchEntity(batch.getId());

        return ImageUploadResponse.builder()
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
            User user,
            long maxFileSizeBytes) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unnamed";
        String extension = getFileExtension(originalFilename);

        // Validate format trực tiếp dựa vào enum ImageFormat
        ImageFormat imageFormat = parseImageFormat(extension);
        if (imageFormat == null) {
            return SingleImageResult.failure(originalFilename + ": Unsupported format '" + extension
                    + "'. Allowed formats: " + Arrays.toString(ImageFormat.values()));
        }

        // Validate size
        if (file.getSize() > maxFileSizeBytes) {
            return SingleImageResult.failure(originalFilename + ": File size (" + (file.getSize() / 1024 / 1024)
                    + "MB) exceeds limit of " + maxFileSizeMb + "MB");
        }

        try {
            // Đọc kích thước ảnh width x height
            int width = 0;
            int height = 0;
            try (InputStream is = file.getInputStream()) {
                BufferedImage bi = ImageIO.read(is);
                if (bi != null) {
                    width = bi.getWidth();
                    height = bi.getHeight();
                }
            } catch (Exception e) {
                log.warn("Could not read image dimensions for {}: {}", originalFilename, e.getMessage());
            }

            // Đặt tên file ngẫu nhiên trên MinIO
            String fileId = UUID.randomUUID().toString();
            String originalObjectName = fileId + "." + extension.toLowerCase();
            String thumbObjectName = fileId + "_thumb." + extension.toLowerCase();

            // Upload ảnh gốc lên MinIO
            minioStorageService.uploadImage(file, originalObjectName);

            // Sinh thumbnail và upload lên MinIO
            try {
                minioStorageService.uploadThumbnail(file, thumbObjectName);
            } catch (Exception e) {
                log.warn("Thumbnail generation failed for {}: {}", originalFilename, e.getMessage());
                thumbObjectName = null;
            }

            // Lưu Image entity vào PostgreSQL (Core service chỉ lưu Image)
            Image image = Image.builder()
                    .path(originalObjectName)
                    .thumbnailPath(thumbObjectName)
                    .uploadedBy(user)
                    .width(width)
                    .height(height)
                    .fileSize(file.getSize())
                    .fileFormat(imageFormat)
                    .type(ImageType.UPLOAD)
                    .build();

            image = imageRepository.save(image);

            String fullImageUrl = minioStorageService.getImageUrl(originalObjectName);

            ImageUploadedEvent.ImageItem item = ImageUploadedEvent.ImageItem.builder()
                    .imageId(image.getId())
                    .imageUrl(fullImageUrl)
                    .fileFormat(imageFormat)
                    .build();

            return SingleImageResult.success(item);

        } catch (Exception e) {
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
            return ImageFormat.valueOf(extension.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static class SingleImageResult {
        private final boolean success;
        private final ImageUploadedEvent.ImageItem item;
        private final String errorMessage;

        private SingleImageResult(boolean success, ImageUploadedEvent.ImageItem item, String errorMessage) {
            this.success = success;
            this.item = item;
            this.errorMessage = errorMessage;
        }

        public static SingleImageResult success(ImageUploadedEvent.ImageItem item) {
            return new SingleImageResult(true, item, null);
        }

        public static SingleImageResult failure(String errorMessage) {
            return new SingleImageResult(false, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public ImageUploadedEvent.ImageItem getItem() {
            return item;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
