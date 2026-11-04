package com.visualsearch.photoimport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualsearch.indexing.client.ai.AiBatchResponse;
import com.visualsearch.indexing.client.ai.AiServiceClient;
import com.visualsearch.indexing.client.qdrant.QdrantIndexClient;
import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.image.BoundedHttpImageSource;
import com.visualsearch.indexing.image.ImagePreprocessor;
import com.visualsearch.indexing.image.PreparedImage;
import com.visualsearch.indexing.persistence.IndexStatus;
import com.visualsearch.indexing.persistence.IndexWrite;
import com.visualsearch.indexing.persistence.IndexingJdbcRepository;
import com.visualsearch.indexing.processing.AiResultMapper;
import com.visualsearch.indexing.resilience.AiCircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class PhotoImporter {
    private static final Logger log = LoggerFactory.getLogger(PhotoImporter.class);
    private static final long LOCK_KEY = PhotoCsv.BATCH.getMostSignificantBits() ^ PhotoCsv.BATCH.getLeastSignificantBits();
    private final DataSource dataSource;
    private final IndexingProperties properties;
    private final List<AiServiceClient> aiClients;
    private final QdrantIndexClient qdrant;
    private final BoundedHttpImageSource source;
    private final ImagePreprocessor preprocessor;
    private final AiResultMapper mapper = new AiResultMapper();

    PhotoImporter(DataSource dataSource, IndexingProperties properties) {
        this(dataSource, properties, List.of(properties.getAi().getBaseUrl()));
    }

    PhotoImporter(DataSource dataSource, IndexingProperties properties, List<String> aiUrls) {
        if (aiUrls.isEmpty() || aiUrls.size() > 2 || aiUrls.stream().anyMatch(String::isBlank)
                || aiUrls.stream().distinct().count() != aiUrls.size()) {
            throw new IllegalArgumentException("Configure one or two distinct AI URLs");
        }
        this.dataSource = dataSource;
        this.properties = properties;
        // These properties belong exclusively to this CLI context.
        properties.getImage().setAllowedHosts(List.of("images.unsplash.com"));
        properties.getImage().setMaxDimension(1024);
        aiClients = aiUrls.stream().map(url -> {
            var clientProperties = new IndexingProperties();
            org.springframework.beans.BeanUtils.copyProperties(properties.getAi(), clientProperties.getAi());
            clientProperties.getAi().setBaseUrl(url);
            return new AiServiceClient(clientProperties, new AiCircuitBreaker(clientProperties));
        }).toList();
        qdrant = new QdrantIndexClient(properties);
        source = new BoundedHttpImageSource(properties);
        preprocessor = new ImagePreprocessor(properties);
    }

    void run(List<PhotoCsv.Photo> photos) throws Exception {
        // One physical connection owns both the session lock and all transactions.
        // A disconnect stops this run, rather than silently reacquiring a pooled connection.
        try (var connection = dataSource.getConnection()) {
            var session = new SingleConnectionDataSource(connection, true);
            var jdbc = new JdbcTemplate(session);
            boolean locked = Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, LOCK_KEY));
            if (!locked) throw new IllegalStateException("Another importer owns this batch");
            try {
                var transaction = new TransactionTemplate(new JdbcTransactionManager(session));
                var repository = new IndexingJdbcRepository(new NamedParameterJdbcTemplate(jdbc), new ObjectMapper());
                int offset = validate(jdbc, photos);
                try (var workers = Executors.newFixedThreadPool(aiClients.size())) {
                    try {
                        var pending = new ArrayDeque<GroupWork>();
                        int submitted = offset;
                        for (int client = 0; client < aiClients.size() && submitted < photos.size(); client++) {
                            var work = submit(workers, photos, submitted, client);
                            pending.addLast(work);
                            submitted = work.end();
                        }
                        while (!pending.isEmpty()) {
                            requireRunning();
                            var work = pending.removeFirst();
                            int end = work.end();
                            var group = photos.subList(offset, end);
                            var writes = await(work.result());
                            requireRunning();
                            int expected = offset;
                            transaction.executeWithoutResult(status -> {
                                var counts = jdbc.queryForMap("select success_count, failed_count, status from batch_index where id = ? for update", PhotoCsv.BATCH);
                                if (((Number) counts.get("success_count")).intValue() + ((Number) counts.get("failed_count")).intValue() != expected
                                        || !"PROCESSING".equals(counts.get("status"))) {
                                    throw new IllegalStateException("Batch changed while importing");
                                }
                                for (var photo : group) insert(jdbc, photo);
                                if (repository.recordOutcomes(writes.values()).size() != group.size()) {
                                    throw new IllegalStateException("Not all outcomes were committed");
                                }
                            });
                            for (int index = 0; index < group.size(); index++) {
                                var photo = group.get(index);
                                var write = writes.get(photo.imageId());
                                log.info("CSV row={} imageId={} status={} error={}", offset + index + 1, photo.imageId(), write.status(), write.error());
                            }
                            offset = end;
                            log.info("Checkpoint {}/{} {}", offset, photos.size(), jdbc.queryForMap(
                                    "select success_count, failed_count, status from batch_index where id = ?", PhotoCsv.BATCH));
                            if (submitted < photos.size()) {
                                var next = submit(workers, photos, submitted, work.client());
                                pending.addLast(next);
                                submitted = next.end();
                            }
                        }
                    } finally {
                        workers.shutdownNow();
                    }
                }
                log.info("Import completed {}", jdbc.queryForMap("select * from batch_index where id = ?", PhotoCsv.BATCH));
            } finally {
                if (!connection.isClosed()) jdbc.queryForObject("select pg_advisory_unlock(?)", Boolean.class, LOCK_KEY);
            }
        }
    }

    private int validate(JdbcTemplate jdbc, List<PhotoCsv.Photo> photos) {
        var batch = jdbc.queryForMap("select * from batch_index where id = ?", PhotoCsv.BATCH);
        int success = ((Number) batch.get("success_count")).intValue();
        int failed = ((Number) batch.get("failed_count")).intValue();
        int processed = Math.addExact(success, failed);
        String status = (String) batch.get("status");
        if (!PhotoCsv.UPLOADER.equals(batch.get("uploaded_by"))
                || ((Number) batch.get("total_images")).intValue() != photos.size()
                || success < 0 || failed < 0 || processed > photos.size()
                || !(processed == photos.size() ? "COMPLETED".equals(status) : "PROCESSING".equals(status))) {
            throw new IllegalStateException("Unexpected batch metadata or counters");
        }
        var rows = jdbc.queryForList("""
                select i.image_id, i.status, i.retry_count, m.path, m.thumbnail_path, m.width, m.height, m.uploaded_by, m.type, m.deleted_at
                from image_index i join images m on m.id = i.image_id where i.batch_id = ?
                """, PhotoCsv.BATCH);
        Map<UUID, Map<String, Object>> indexed = new LinkedHashMap<>();
        rows.forEach(row -> indexed.put((UUID) row.get("image_id"), row));
        if (rows.size() != processed || indexed.size() != processed) throw new IllegalStateException("Checkpoint row count mismatch");
        int actualSuccess = 0;
        for (var photo : photos.subList(0, processed)) {
            var row = indexed.get(photo.imageId());
            if (row == null || !photo.url().equals(row.get("path")) || !photo.thumbnail().equals(row.get("thumbnail_path"))
                    || !Integer.valueOf(photo.width()).equals(row.get("width")) || !Integer.valueOf(photo.height()).equals(row.get("height"))
                    || !PhotoCsv.UPLOADER.equals(row.get("uploaded_by")) || !"UPLOAD".equals(row.get("type"))
                    || row.get("deleted_at") != null || !Integer.valueOf(0).equals(row.get("retry_count"))
                    || !List.of("SUCCESS", "FAILED").contains(row.get("status"))) {
                throw new IllegalStateException("CSV prefix does not match committed image " + photo.imageId());
            }
            if ("SUCCESS".equals(row.get("status"))) actualSuccess++;
        }
        if (actualSuccess != success) throw new IllegalStateException("Checkpoint success count mismatch");
        log.info("Resume after {} committed images, next CSV row={}", processed, processed + 1);
        return processed;
    }

    private record GroupWork(int end, int client, Future<Map<UUID, IndexWrite>> result) {}

    private GroupWork submit(ExecutorService workers, List<PhotoCsv.Photo> photos, int offset, int client) {
        requireRunning();
        int end = Math.min(offset + Math.min(3, properties.getAi().getBatchSize()), photos.size());
        var result = workers.submit(() -> {
            log.info("Processing CSV rows={}..{} aiInstance={}", offset + 1, end, client + 1);
            var writes = process(photos.subList(offset, end), aiClients.get(client));
            requireRunning();
            return writes;
        });
        return new GroupWork(end, client, result);
    }

    private static Map<UUID, IndexWrite> await(Future<Map<UUID, IndexWrite>> result) throws Exception {
        try {
            return result.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Import interrupted while waiting for AI", exception);
        } catch (ExecutionException exception) {
            if (exception.getCause() instanceof Exception cause) throw cause;
            throw new IllegalStateException("Import worker failed", exception.getCause());
        }
    }

    private Map<UUID, IndexWrite> process(List<PhotoCsv.Photo> group, AiServiceClient ai) {
        Map<UUID, IndexWrite> writes = new LinkedHashMap<>();
        List<PreparedImage> prepared = new ArrayList<>();
        for (var photo : group) {
            requireRunning();
            try {
                prepared.add(preprocessor.preprocess(photo.imageId(), source.download(photo.downloadUrl())));
            } catch (RuntimeException failure) {
                requireRunning();
                writes.put(photo.imageId(), failed(photo.imageId(), failure.getMessage()));
            }
        }
        if (prepared.isEmpty()) return writes;
        AiBatchResponse response;
        try {
            response = ai.indexOnce(prepared);
        } catch (RuntimeException failure) {
            requireRunning();
            prepared.forEach(image -> writes.put(image.imageId(), failed(image.imageId(), failure.getMessage())));
            return writes;
        }
        Map<UUID, AiBatchResponse.Result> successes = new LinkedHashMap<>();
        for (var image : prepared) {
            var matches = response.results().stream().filter(result -> image.imageId().equals(result.imageId())).toList();
            if (matches.size() != 1 || !matches.getFirst().successful()) {
                writes.put(image.imageId(), failed(image.imageId(), "AI failed, missing or duplicate result: " + matches));
            } else successes.put(image.imageId(), matches.getFirst());
        }
        requireRunning();
        var failures = qdrant.upsertOnce(successes.entrySet().stream().map(entry ->
                new QdrantIndexClient.Point(entry.getKey(), PhotoCsv.BATCH, entry.getValue().safeEmbedding())).toList());
        successes.forEach((id, result) -> {
            if (failures.containsKey(id)) writes.put(id, failed(id, failures.get(id).getMessage()));
            else writes.put(id, new IndexWrite(id, IndexStatus.SUCCESS, 0, null,
                    result.safeOcr().stream().map(mapper::toOcrData).toList()));
        });
        return writes;
    }

    private static IndexWrite failed(UUID id, String error) {
        return new IndexWrite(id, IndexStatus.FAILED, 0, error, List.of());
    }

    private static void insert(JdbcTemplate jdbc, PhotoCsv.Photo photo) {
        jdbc.update("""
                insert into images(id, path, thumbnail_path, width, height, uploaded_by, type, file_format, file_size, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, 'UPLOAD', ?, null, now(), now())
                """, photo.imageId(), photo.url(), photo.thumbnail(), photo.width(), photo.height(), PhotoCsv.UPLOADER, photo.format());
        jdbc.update("""
                insert into image_index(id, image_id, batch_id, status, retry_count, created_at, updated_at)
                values (?, ?, ?, 'PENDING', 0, now(), now())
                """, UUID.randomUUID(), photo.imageId(), PhotoCsv.BATCH);
    }

    private static void requireRunning() {
        if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Import interrupted");
    }
}
