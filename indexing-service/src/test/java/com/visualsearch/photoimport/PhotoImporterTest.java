package com.visualsearch.photoimport;

import com.visualsearch.indexing.client.ai.AiBatchResponse;
import com.visualsearch.indexing.client.ai.AiServiceClient;
import com.visualsearch.indexing.client.qdrant.QdrantIndexClient;
import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.image.BoundedHttpImageSource;
import com.visualsearch.indexing.image.DownloadedImage;
import com.visualsearch.indexing.image.PreparedImage;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PhotoImporterTest {
    @TempDir Path temp;

    @Test
    void cliContextHasNoWorkerOrMessagingOrWebServer() {
        try (var context = new org.springframework.boot.builder.SpringApplicationBuilder(PhotoImportApplication.class)
                .web(org.springframework.boot.WebApplicationType.NONE)
                .run("--spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused", "--spring.main.banner-mode=off")) {
            assertTrue(context.getBeansOfType(com.visualsearch.indexing.messaging.IndexingJobConsumer.class).isEmpty());
            assertTrue(context.getBeansOfType(org.springframework.amqp.rabbit.connection.ConnectionFactory.class).isEmpty());
            assertTrue(context.getBeansOfType(org.springframework.boot.web.servlet.server.ServletWebServerFactory.class).isEmpty());
            assertTrue(context.getBeansOfType(com.visualsearch.indexing.resilience.AiHealthMonitor.class).isEmpty());
        }
        var scanner = new org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider(true);
        assertTrue(scanner.findCandidateComponents("com.visualsearch.indexing").stream()
                .noneMatch(bean -> bean.getBeanClassName().contains("photoimport")));
    }

    @Test
    void csvPinsOrderAndPreservesOriginalUrls() throws Exception {
        String csv = "id,image_url,thumbnail_url,width,height\na,https://images.unsplash.com/a,https://images.unsplash.com/a?w=400&h=400,3000,2000\n";
        var path = temp.resolve("photos.csv");
        Files.writeString(path, csv);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        var photos = PhotoCsv.read(path, hash, 1);
        var photo = photos.getFirst();
        assertEquals("https://images.unsplash.com/a", photo.url());
        assertEquals("https://images.unsplash.com/a?w=1024&h=1024&fit=max&fm=jpg", photo.downloadUrl());
        assertEquals(photo.imageId(), new PhotoCsv.Photo("a", "other", "", 1, 1).imageId());
        Files.writeString(path, csv.replace("/a", "/b"));
        assertThrows(IllegalStateException.class, () -> PhotoCsv.read(path, hash, 1));
        assertThrows(IllegalArgumentException.class, () -> photo("bad", "https://images.unsplash.company").downloadUrl());
    }

    private static PhotoCsv.Photo photo(String id, String url) {
        return new PhotoCsv.Photo(id, url, url + "?w=400&h=400", 3000, 2000);
    }

    @EnabledIfEnvironmentVariable(named = "PHOTO_IMPORT_TEST_DB", matches = ".+")
    @org.junit.jupiter.api.Nested
    class DatabaseTests {
        DriverManagerDataSource admin;
        DriverManagerDataSource dataSource;
        JdbcTemplate jdbc;
        String schema;
        HttpServer server;
        PhotoImporter importer;
        AtomicInteger aiCalls = new AtomicInteger();
        AtomicInteger upserts = new AtomicInteger();
        volatile boolean rejectUpsert;
        volatile boolean rejectAi;
        java.util.Set<UUID> vectorIds = java.util.concurrent.ConcurrentHashMap.newKeySet();
        List<PhotoCsv.Photo> photos;

        @BeforeEach
        void setup() throws Exception {
            String url = System.getenv("PHOTO_IMPORT_TEST_DB");
            String user = System.getenv().getOrDefault("PHOTO_IMPORT_TEST_USER", "postgres");
            String password = System.getenv().getOrDefault("PHOTO_IMPORT_TEST_PASSWORD", "import_test");
            admin = new DriverManagerDataSource(url, user, password);
            schema = "import_test_" + UUID.randomUUID().toString().replace("-", "");
            new JdbcTemplate(admin).execute("create schema " + schema);
            dataSource = new DriverManagerDataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema, user, password);
            jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("create table batch_index(id uuid primary key, uploaded_by uuid, total_images int, success_count int, failed_count int, status varchar, total_duration_ms bigint, created_at timestamptz, updated_at timestamptz)");
            jdbc.execute("create table images(id uuid primary key, path varchar(500), thumbnail_path varchar(500), width int, height int, uploaded_by uuid, type varchar, file_format varchar, file_size bigint, deleted_at timestamptz, created_at timestamptz, updated_at timestamptz)");
            jdbc.execute("create table image_index(id uuid primary key, image_id uuid unique references images(id), batch_id uuid references batch_index(id), status varchar, retry_count int, error_message text, created_at timestamptz, updated_at timestamptz)");
            jdbc.execute("create table image_ocr(id uuid primary key, image_id uuid references images(id), raw_text text, normalized_text text, confidence_score real, bounding_box jsonb, created_at timestamptz, updated_at timestamptz)");
            photos = List.of(photo("a", "https://images.unsplash.com/a"), photo("b", "https://images.unsplash.company"), photo("c", "https://images.unsplash.com/c"), photo("d", "https://images.unsplash.com/d"));
            jdbc.update("insert into batch_index values (?, ?, 4, 0, 0, 'PROCESSING', 0, now(), now())", PhotoCsv.BATCH, PhotoCsv.UPLOADER);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            var objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            server.createContext("/api/v1/indexing/batch", exchange -> {
                aiCalls.incrementAndGet();
                String body = new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.ISO_8859_1);
                if (rejectAi) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                    return;
                }
                var results = photos.stream().filter(p -> body.contains(p.imageId().toString())).map(p -> new AiBatchResponse.Result(
                        p.imageId(), "SUCCESS", List.of(0.1f, 0.2f), List.of(new AiBatchResponse.OcrItem("hello", "hello", 0.9f, null)), null)).toList();
                byte[] json = objectMapper.writeValueAsBytes(new AiBatchResponse(results));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                exchange.getResponseBody().write(json);
                exchange.close();
            });
            server.createContext("/collections/images", exchange -> {
                byte[] body = exchange.getRequestBody().readAllBytes();
                boolean put = "PUT".equals(exchange.getRequestMethod());
                if (put) {
                    upserts.incrementAndGet();
                    if (!rejectUpsert) {
                        for (var point : objectMapper.readTree(body).get("points")) {
                            vectorIds.add(UUID.fromString(point.get("id").asText()));
                        }
                    }
                }
                exchange.sendResponseHeaders(put && rejectUpsert ? 400 : 200, -1);
                exchange.close();
            });
            server.start();
            var properties = new IndexingProperties();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            properties.getAi().setBaseUrl(baseUrl);
            properties.getQdrant().setBaseUrl(baseUrl);
            properties.getQdrant().setVectorSize(2);
            importer = new PhotoImporter(dataSource, properties);
            var source = mock(BoundedHttpImageSource.class);
            var bytes = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(8, 6, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
            when(source.download(anyString())).thenReturn(new DownloadedImage(bytes.toByteArray()));
            ReflectionTestUtils.setField(importer, "source", source);
        }

        @AfterEach
        void cleanup() {
            Thread.interrupted();
            if (server != null) server.stop(0);
            if (admin != null && schema != null) new JdbcTemplate(admin).execute("drop schema " + schema + " cascade");
        }

        @Test
        void commitsAndCompletedRerunDoesNoWork() throws Exception {
            importer.run(photos);
            assertEquals(3, jdbc.queryForObject("select success_count from batch_index", Integer.class));
            assertEquals(1, jdbc.queryForObject("select failed_count from batch_index", Integer.class));
            assertEquals("COMPLETED", jdbc.queryForObject("select status from batch_index", String.class));
            assertEquals(3, jdbc.queryForObject("select count(*) from image_ocr", Integer.class));
            assertEquals(photos.getFirst().url(), jdbc.queryForObject("select path from images where id = ?", String.class, photos.getFirst().imageId()));
            assertEquals(photos.getFirst().thumbnail(), jdbc.queryForObject("select thumbnail_path from images where id = ?", String.class, photos.getFirst().imageId()));
            importer.run(photos);
            assertEquals(2, aiCalls.get());
            assertEquals(4, jdbc.queryForObject("select count(*) from images", Integer.class));
            assertEquals(3, vectorIds.size());
        }

        @Test
        void rollsBackAfterUpsertThenResumesWithoutDuplicates() throws Exception {
            jdbc.execute("alter table images add constraint stop_commit check (width < 100)");
            assertThrows(RuntimeException.class, () -> importer.run(photos));
            assertEquals(1, upserts.get());
            assertEquals(0, jdbc.queryForObject("select count(*) from images", Integer.class));
            assertEquals(0, jdbc.queryForObject("select success_count + failed_count from batch_index", Integer.class));
            jdbc.execute("alter table images drop constraint stop_commit");
            importer.run(photos);
            assertEquals(4, jdbc.queryForObject("select count(*) from images", Integer.class));
            assertEquals(3, vectorIds.size());
        }

        @Test
        void resumesAfterCommittedPrefixAndRejectsMismatch() throws Exception {
            jdbc.execute("alter table images add constraint stop_last check (path <> 'https://images.unsplash.com/d')");
            assertThrows(RuntimeException.class, () -> importer.run(photos));
            assertEquals(3, jdbc.queryForObject("select success_count + failed_count from batch_index", Integer.class));
            jdbc.update("update images set path = 'changed' where id = ?", photos.getFirst().imageId());
            assertThrows(IllegalStateException.class, () -> importer.run(photos));
            jdbc.update("update images set path = ? where id = ?", photos.getFirst().url(), photos.getFirst().imageId());
            jdbc.execute("alter table images drop constraint stop_last");
            int callsBeforeResume = aiCalls.get();
            importer.run(photos);
            assertEquals(callsBeforeResume + 1, aiCalls.get());
        }

        @Test
        void rejectsConcurrentImporter() throws Exception {
            try (var connection = dataSource.getConnection()) {
                var statement = connection.prepareStatement("select pg_advisory_lock(?)");
                statement.setLong(1, PhotoCsv.BATCH.getMostSignificantBits() ^ PhotoCsv.BATCH.getLeastSignificantBits());
                statement.execute();
                assertThrows(IllegalStateException.class, () -> importer.run(photos));
                assertEquals(0, aiCalls.get());
            }
        }

        @Test
        void qdrantFailureHasNoPerPointRetry() throws Exception {
            rejectUpsert = true;
            importer.run(photos);
            assertEquals(2, upserts.get());
            assertEquals(4, jdbc.queryForObject("select failed_count from batch_index", Integer.class));
            assertEquals(0, jdbc.queryForObject("select sum(retry_count) from image_index", Integer.class));
        }

        @Test
        void interruptionDoesNotAdvanceCheckpoint() {
            Thread.currentThread().interrupt();
            assertThrows(IllegalStateException.class, () -> importer.run(photos));
            Thread.interrupted();
            assertEquals(0, jdbc.queryForObject("select count(*) from images", Integer.class));
        }

        @Test
        void interruptionAfterUpsertReusesVectorIdsOnResume() throws Exception {
            var realClient = (QdrantIndexClient) ReflectionTestUtils.getField(importer, "qdrant");
            var interruptedClient = mock(QdrantIndexClient.class);
            when(interruptedClient.upsertOnce(anyList())).thenAnswer(call -> {
                var result = realClient.upsertOnce(call.getArgument(0));
                Thread.currentThread().interrupt();
                return result;
            });
            ReflectionTestUtils.setField(importer, "qdrant", interruptedClient);
            assertThrows(IllegalStateException.class, () -> importer.run(photos));
            Thread.interrupted();
            assertEquals(0, jdbc.queryForObject("select count(*) from images", Integer.class));
            assertEquals(2, vectorIds.size());
            ReflectionTestUtils.setField(importer, "qdrant", realClient);
            importer.run(photos);
            assertEquals(3, vectorIds.size());
            assertEquals(4, jdbc.queryForObject("select count(*) from images", Integer.class));
        }

        @Test
        void aiFailureIsFinalWithoutRetry() throws Exception {
            rejectAi = true;
            importer.run(photos);
            assertEquals(2, aiCalls.get());
            assertEquals(0, upserts.get());
            assertEquals(4, jdbc.queryForObject("select failed_count from batch_index", Integer.class));
            assertEquals(0, jdbc.queryForObject("select sum(retry_count) from image_index", Integer.class));
        }

        private List<AiServiceClient> twoInstances(CountDownLatch firstStarted, CountDownLatch secondFinished) {
            var clients = List.of(mock(AiServiceClient.class), mock(AiServiceClient.class));
            when(clients.getFirst().indexOnce(anyList())).thenAnswer(call -> {
                firstStarted.countDown();
                assertTrue(secondFinished.await(10, TimeUnit.SECONDS), "Second AI must run concurrently");
                assertEquals(0, jdbc.queryForObject("select success_count + failed_count from batch_index", Integer.class),
                        "Later group cannot commit before the first group");
                return response(call.getArgument(0));
            });
            when(clients.getLast().indexOnce(anyList())).thenAnswer(call -> {
                assertTrue(firstStarted.await(10, TimeUnit.SECONDS));
                secondFinished.countDown();
                return response(call.getArgument(0));
            });
            ReflectionTestUtils.setField(importer, "aiClients", clients);
            return clients;
        }

        private AiBatchResponse response(List<PreparedImage> images) {
            return new AiBatchResponse(images.stream().map(image -> new AiBatchResponse.Result(
                    image.imageId(), "SUCCESS", List.of(0.1f, 0.2f), List.of(), null)).toList());
        }

        @Test
        void twoInstancesRunConcurrentlyAndCommitInCsvOrder() throws Exception {
            var clients = twoInstances(new CountDownLatch(1), new CountDownLatch(1));
            importer.run(photos);
            verify(clients.getFirst(), times(1)).indexOnce(anyList());
            verify(clients.getLast(), times(1)).indexOnce(anyList());
            assertEquals(3, jdbc.queryForObject("select success_count from batch_index", Integer.class));
            assertEquals(1, jdbc.queryForObject("select failed_count from batch_index", Integer.class));
            assertEquals("COMPLETED", jdbc.queryForObject("select status from batch_index", String.class));
            importer.run(photos);
            verifyNoMoreInteractions(clients.toArray(AiServiceClient[]::new));
        }

        @Test
        void failedFirstCommitDiscardsLaterResultsAndResumes() throws Exception {
            var clients = twoInstances(new CountDownLatch(1), new CountDownLatch(1));
            jdbc.execute("alter table images add constraint stop_first check (path <> 'https://images.unsplash.com/a')");
            assertThrows(RuntimeException.class, () -> importer.run(photos));
            assertEquals(0, jdbc.queryForObject("select count(*) from images", Integer.class));
            assertEquals(0, jdbc.queryForObject("select success_count + failed_count from batch_index", Integer.class));
            jdbc.execute("alter table images drop constraint stop_first");
            importer.run(photos);
            assertEquals(4, jdbc.queryForObject("select count(*) from images", Integer.class));
            assertEquals(3, vectorIds.size());
            verify(clients.getFirst(), times(2)).indexOnce(anyList());
            verify(clients.getLast(), times(2)).indexOnce(anyList());
        }

        @Test
        void failedSecondInstanceOnlyFailsItsGroup() throws Exception {
            var clients = twoInstances(new CountDownLatch(1), new CountDownLatch(1));
            doThrow(new IllegalStateException("Second AI unavailable")).when(clients.getLast()).indexOnce(anyList());
            // This case tests independent endpoint failure, without the ordering barrier.
            doAnswer(call -> response(call.getArgument(0))).when(clients.getFirst()).indexOnce(anyList());
            importer.run(photos);
            assertEquals(2, jdbc.queryForObject("select success_count from batch_index", Integer.class));
            assertEquals(2, jdbc.queryForObject("select failed_count from batch_index", Integer.class));
            assertEquals(0, jdbc.queryForObject("select sum(retry_count) from image_index", Integer.class));
        }
    }
}
