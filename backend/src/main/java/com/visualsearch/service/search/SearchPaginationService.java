package com.visualsearch.service.search;

import com.visualsearch.config.JwtProperties;
import com.visualsearch.dto.search.SearchPageData;
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

    private String encode(UUID searchId, long offset, int size) {
        String payload = ENCODER.encodeToString(("search-v1:" + searchId + ":" + offset + ":" + size)
                .getBytes(StandardCharsets.UTF_8));
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
            if (fields.length != 4 || !"search-v1".equals(fields[0]) || !searchId.toString().equals(fields[1])) {
                throw new IllegalArgumentException();
            }
            long offset = Long.parseLong(fields[2]);
            int size = Integer.parseInt(fields[3]);
            if (offset < 1 || offset > Long.MAX_VALUE - MAX_PAGE_SIZE - 1
                    || size < 1 || size > MAX_PAGE_SIZE) {
                throw new IllegalArgumentException();
            }
            return new Cursor(offset, size);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid search cursor");
        }
    }

    public void validatePageSize(int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    public SearchPageData createPage(UUID searchId, SearchType searchType, int size,
                                     SimilaritySearchService.ResultPage results) {
        String nextCursor = results.nextOffset() == null ? null : encode(searchId, results.nextOffset(), size);
        return new SearchPageData(searchId, searchType, results.items(), size, nextCursor != null, nextCursor);
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

    public record Cursor(long offset, int size) {}
}
