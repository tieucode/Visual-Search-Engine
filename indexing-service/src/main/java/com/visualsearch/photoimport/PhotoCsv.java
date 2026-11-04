package com.visualsearch.photoimport;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.StringReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** CSV metadata only. Image bytes are never retained here. */
final class PhotoCsv {
    static final String SHA256 = "d9f87e961bbbe72ede2f90e8d5179d3d8162f9c498a19c65f77b0d37148aaaa2";
    static final UUID BATCH = UUID.fromString("8c8379f6-8bf8-4c2a-9c16-71b9e22fcf78");
    static final UUID UPLOADER = UUID.fromString("1d67f029-c68b-43fb-a884-92cba9801d7c");

    record Photo(String sourceId, String url, String thumbnail, int width, int height) {
        UUID imageId() {
            return UUID.nameUUIDFromBytes((BATCH + ":" + sourceId).getBytes(StandardCharsets.UTF_8));
        }

        String downloadUrl() {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"images.unsplash.com".equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getPort() != -1
                    || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw new IllegalArgumentException("Unsupported source URL: " + url);
            }
            return url + "?w=1024&h=1024&fit=max&fm=jpg";
        }

        String format() {
            String path = URI.create(url).getPath();
            String extension = path == null ? "" : path.substring(path.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
            return List.of("JPG", "JPEG", "PNG", "WEBP", "GIF", "BMP").contains(extension)
                    ? extension : "JPEG";
        }
    }

    static List<Photo> read(Path path, String expectedHash, int expectedCount) throws Exception {
        byte[] metadata = Files.readAllBytes(path);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(metadata));
        if (!expectedHash.equalsIgnoreCase(hash)) throw new IllegalStateException("CSV SHA-256 mismatch: " + hash);
        var format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(false).build();
        try (var parser = new CSVParser(new StringReader(new String(metadata, StandardCharsets.UTF_8)), format)) {
            if (!parser.getHeaderNames().equals(List.of("id", "image_url", "thumbnail_url", "width", "height"))) {
                throw new IllegalStateException("Unexpected CSV header");
            }
            var photos = new java.util.ArrayList<Photo>();
            var ids = new HashSet<String>();
            for (var row : parser) {
                if (!row.isConsistent()) throw new IllegalStateException("Invalid CSV row " + row.getRecordNumber());
                var photo = new Photo(row.get("id"), row.get("image_url"), row.get("thumbnail_url"),
                        Integer.parseInt(row.get("width")), Integer.parseInt(row.get("height")));
                if (photo.sourceId().isBlank() || !ids.add(photo.sourceId()) || photo.url().isBlank()
                        || photo.url().length() > 500 || photo.thumbnail().length() > 500
                        || photo.width() <= 0 || photo.height() <= 0) {
                    throw new IllegalStateException("Invalid CSV metadata at row " + row.getRecordNumber());
                }
                photos.add(photo);
            }
            if (photos.size() != expectedCount) throw new IllegalStateException("Unexpected CSV record count " + photos.size());
            return List.copyOf(photos);
        }
    }
}
