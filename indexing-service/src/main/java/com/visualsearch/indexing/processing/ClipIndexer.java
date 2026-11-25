package com.visualsearch.indexing.processing;

import com.visualsearch.indexing.client.ai.AiBatchResponse;
import com.visualsearch.indexing.client.ai.AiServiceClient;
import com.visualsearch.indexing.client.qdrant.QdrantIndexClient;
import com.visualsearch.indexing.exception.AiServiceException;
import com.visualsearch.indexing.exception.TransientDependencyException;
import com.visualsearch.indexing.image.ClipFrameExtractor.Frame;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.persistence.ImageState;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ClipIndexer {
    private final AiServiceClient ai;
    private final QdrantIndexClient qdrant;
    private final MinioClient minio;

    @Value("${minio.bucket-thumbnails:thumbnails}")
    private String thumbnails;

    public void index(ImageState state, List<Frame> frames, Runnable onCallAccepted) {
        long started = System.nanoTime();
        List<PreparedImage> inputs = frames.stream()
                .map(frame -> new PreparedImage(UUID.nameUUIDFromBytes(
                        (state.imageId() + ":" + frame.timestampMs()).getBytes(StandardCharsets.UTF_8)), frame.jpeg()))
                .toList();
        var results = ai.embed(inputs, onCallAccepted).results().stream()
                .collect(Collectors.groupingBy(AiBatchResponse.Result::imageId));
        List<List<Float>> vectors = new ArrayList<>();
        for (PreparedImage input : inputs) {
            var matches = results.getOrDefault(input.imageId(), List.of());
            if (matches.size() != 1) throw new AiServiceException("Missing or duplicate frame embedding", true);
            var result = matches.getFirst();
            if (!result.successful()) {
                var error = result.error();
                throw new AiServiceException(error == null ? "Frame embedding failed" : error.message(),
                        error == null || error.isRetryable());
            }
            vectors.add(result.safeEmbedding());
        }
        long embedded = System.nanoTime();

        List<Integer> timestamps = frames.stream().map(Frame::timestampMs).toList();
        for (Frame frame : frames) storeThumbnail(state.imageId(), frame);
        qdrant.upsertClip(state.imageId(), vectors, timestamps);
        log.info("Clip indexed clipId={} frames={} embeddingMs={} writeMs={}", state.imageId(), frames.size(),
                TimeUnit.NANOSECONDS.toMillis(embedded - started),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - embedded));
    }

    private void storeThumbnail(UUID clipId, Frame frame) {
        String key = "clips/" + clipId + "/" + frame.timestampMs() + ".jpg";
        try (var input = new ByteArrayInputStream(frame.jpeg())) {
            minio.putObject(PutObjectArgs.builder().bucket(thumbnails).object(key)
                    .stream(input, frame.jpeg().length, -1).contentType("image/jpeg").build());
        } catch (Exception exception) {
            throw new TransientDependencyException("Could not store clip frame thumbnail", exception);
        }
    }
}
