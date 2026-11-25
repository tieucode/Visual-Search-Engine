package com.visualsearch.service.search;

import com.visualsearch.config.JwtProperties;
import com.visualsearch.dto.search.SearchPageData;
import com.visualsearch.dto.search.SearchImageData;
import com.visualsearch.enums.SearchType;
import com.visualsearch.exception.BadRequestException;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

/** Opaque, signed pagination state; never an authentication token or result cache. */
@Service
public class SearchPaginationService {
    public static final String DEFAULT_PAGE_SIZE = "40";
    public static final int MAX_PAGE_SIZE = 100;

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final SecretKeySpec key;

    public SearchPaginationService(JwtProperties properties) {
        key = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String encodeCursor(UUID searchId, long offset, int size) {
        return encodeCursor(searchId, offset, null, size);
    }

    private String encodeCursor(UUID searchId, long offset, Long clipOffset, int size) {
        if (searchId == null || !validPosition(offset, clipOffset, size)) {
            throw new BadRequestException("Invalid search cursor position");
        }
        String fields = clipOffset == null ? "search-v1:" + searchId + ":" + offset + ":" + size
                : "search-v2:" + searchId + ":" + offset + ":" + clipOffset + ":" + size;
        String payload = ENCODER.encodeToString(fields.getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENCODER.encodeToString(sign(payload));
    }

    public Cursor decodeCursor(String token, UUID searchId) {
        try {
            if (token == null || token.length() > 512) {
                throw new IllegalArgumentException();
            }
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1]))) {
                throw new IllegalArgumentException();
            }
            String[] fields = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8).split(":", -1);
            boolean mixed = fields.length == 5 && "search-v2".equals(fields[0]);
            boolean legacy = fields.length == 4 && "search-v1".equals(fields[0]);
            if ((!mixed && !legacy) || !searchId.toString().equals(fields[1])) {
                throw new IllegalArgumentException();
            }
            long offset = Long.parseLong(fields[2]);
            Long clipOffset = mixed ? Long.parseLong(fields[3]) : null;
            int size = Integer.parseInt(fields[mixed ? 4 : 3]);
            if (!validPosition(offset, clipOffset, size)) {
                throw new IllegalArgumentException();
            }
            return new Cursor(offset, clipOffset, size);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid search cursor");
        }
    }

    private boolean validPosition(long offset, Long clipOffset, int size) {
        long maximum = Long.MAX_VALUE - MAX_PAGE_SIZE - 1;
        return size >= 1 && size <= MAX_PAGE_SIZE && offset >= 0 && offset <= maximum
                && (clipOffset == null ? offset > 0
                : clipOffset >= 0 && clipOffset <= maximum && (offset > 0 || clipOffset > 0));
    }

    public String encodeOcrCursor(UUID searchId, double score, UUID imageId, int size) {
        if (searchId == null || imageId == null || !Double.isFinite(score)
                || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("Invalid OCR search cursor position");
        }
        String payload = ENCODER.encodeToString(("ocr-v1:" + searchId + ":" + Double.toHexString(score)
                + ":" + imageId + ":" + size).getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENCODER.encodeToString(sign(payload));
    }

    public OcrCursor decodeOcrCursor(String token, UUID searchId) {
        try {
            if (token == null || token.length() > 512) throw new IllegalArgumentException();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), DECODER.decode(parts[1])))
                throw new IllegalArgumentException();
            String[] fields = new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8).split(":", -1);
            if (fields.length != 5 || !"ocr-v1".equals(fields[0]) || !searchId.toString().equals(fields[1]))
                throw new IllegalArgumentException();
            double score = Double.valueOf(fields[2]);
            UUID imageId = UUID.fromString(fields[3]);
            int size = Integer.parseInt(fields[4]);
            if (!Double.isFinite(score) || size < 1 || size > MAX_PAGE_SIZE)
                throw new IllegalArgumentException();
            return new OcrCursor(score, imageId, size);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid search cursor");
        }
    }

    public void validatePageSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public SearchPageData<SearchImageData> createPage(UUID searchId, SearchType searchType, int size,
                                                       SimilaritySearchService.ResultPage results) {
        String nextCursor = results.nextOffset() == null ? null
                : encodeCursor(searchId, results.nextOffset(), results.nextClipOffset(), size);
        return new SearchPageData<>(searchId, searchType, results.items(), size, nextCursor != null, nextCursor);
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not sign search cursor", exception);
        }
    }

    public record Cursor(long offset, Long clipOffset, int size) {}
    public record OcrCursor(double score, UUID imageId, int size) {}
}
