package com.visualsearch.service;

import com.visualsearch.entity.BatchIndex;
import com.visualsearch.entity.Image;
import com.visualsearch.entity.ImageIndex;
import com.visualsearch.entity.IndexingJobOutbox;
import com.visualsearch.entity.User;
import com.visualsearch.enums.ImageFormat;
import com.visualsearch.enums.ImageType;
import com.visualsearch.enums.IndexStatus;
import com.visualsearch.enums.MediaKind;
import com.visualsearch.repository.BatchIndexRepository;
import com.visualsearch.repository.ImageIndexRepository;
import com.visualsearch.repository.ImageRepository;
import com.visualsearch.repository.IndexingJobOutboxRepository;
import com.visualsearch.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IndexingJobPersistenceService {

    private final ImageRepository imageRepository;
    private final ImageIndexRepository imageIndexRepository;
    private final IndexingJobOutboxRepository indexingJobOutboxRepository;
    private final BatchIndexRepository batchIndexRepository;
    private final UserRepository userRepository;

    @Transactional
    public void createImageAndPendingIndex(
            UUID batchId,
            UUID userId,
            String imagePath,
            String thumbnailPath,
            int width,
            int height,
            long fileSize,
            ImageFormat imageFormat,
            MediaKind mediaKind,
            Integer durationMs) {
        BatchIndex batch = batchIndexRepository.getReferenceById(batchId);
        User user = userRepository.getReferenceById(userId);

        Image image = Image.builder()
                .path(imagePath)
                .thumbnailPath(thumbnailPath)
                .uploadedBy(user)
                .width(width)
                .height(height)
                .fileSize(fileSize)
                .fileFormat(imageFormat)
                .mediaKind(mediaKind)
                .durationMs(durationMs)
                .type(ImageType.UPLOAD)
                .build();
        image = imageRepository.save(image);

        ImageIndex imageIndex = ImageIndex.builder()
                .image(image)
                .batch(batch)
                .status(IndexStatus.PENDING)
                .retryCount(0)
                .build();
        imageIndexRepository.save(imageIndex);
        indexingJobOutboxRepository.save(new IndexingJobOutbox(image.getId()));
    }
}
