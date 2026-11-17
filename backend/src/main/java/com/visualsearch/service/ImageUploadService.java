package com.visualsearch.service;

import com.visualsearch.dto.upload.ImageUploadRequest;
import com.visualsearch.dto.upload.ImageUploadData;
import com.visualsearch.entity.BatchIndex;
import com.visualsearch.entity.User;
import com.visualsearch.enums.BatchStatus;
import com.visualsearch.enums.ImageFormat;
import com.visualsearch.enums.IndexStatus;
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
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.CompletionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImageUploadService {

    private final MinioStorageService minioStorageService;
    private final BatchService batchService;
    private final IndexingJobPersistenceService indexingJobPersistenceService;
    private final ImageIndexRepository imageIndexRepository;

    @Qualifier("uploadExecutor")
    private final Executor uploadExecutor;

    @Value("${app.upload.max-file-size-mb:10}")
    private long maxFileSizeMb;

    @Value("${app.upload.max-image-pixels:40000000}")
    private long maxImagePixels;

    @PostConstruct
    public void registerImageReaders() {
        if (maxImagePixels < 1 || maxFileSizeMb < 1) {
            throw new IllegalStateException("Upload size and pixel limits must be positive");
        }
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
        int uploadedCount = 0;
        List<String> errors = new ArrayList<>();

        for (CompletableFuture<SingleImageResult> future : futures) {
            try {
                SingleImageResult result = future.join();
                if (result.isSuccess()) {
                    uploadedCount++;
                } else {
                    errors.add(result.getErrorMessage());
                }
            } catch (CompletionException e) {
                log.error("Error retrieving upload result", e);
                errors.add("Internal processing error");
            }
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
                .uploadedCount(uploadedCount)
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

            ImageDimensions dimensions = readDimensions(file);

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
                minioStorageService.deleteThumbnail(thumbObjectName);
                thumbObjectName = null;
            }

            String fullImageUrl = minioStorageService.getImageUrl(originalObjectName);

            // Lưu Image và trạng thái indexing ban đầu trong cùng transaction.
            indexingJobPersistenceService.createImageAndPendingIndex(
                    batchId,
                    userId,
                    originalObjectName,
                    thumbObjectName,
                    dimensions.width(),
                    dimensions.height(),
                    file.getSize(),
                    imageFormat,
                    fullImageUrl);
            persisted = true;
            return SingleImageResult.success();

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
            return SingleImageResult.failure(originalFilename + ": "
                    + (e instanceof BadRequestException ? e.getMessage() : "Image processing failed"));
        }
    }

    private ImageDimensions readDimensions(MultipartFile file) {
        try (InputStream input = file.getInputStream();
             var imageInput = new MemoryCacheImageInputStream(input)) {
            var readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new BadRequestException("Image cannot be decoded");
            var reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > maxImagePixels) {
                    throw new BadRequestException("Image dimensions exceed the supported pixel limit");
                }
                var decoded = reader.read(0);
                if (decoded == null) throw new BadRequestException("Image cannot be decoded");
                decoded.flush();
                return new ImageDimensions(width, height);
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new BadRequestException("Image cannot be decoded");
        }
    }

    private record ImageDimensions(int width, int height) {}

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
        private final String errorMessage;

        private SingleImageResult(boolean success, String errorMessage) {
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public static SingleImageResult success() {
            return new SingleImageResult(true, null);
        }

        public static SingleImageResult failure(String errorMessage) {
            return new SingleImageResult(false, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}
