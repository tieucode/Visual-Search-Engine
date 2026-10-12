package com.visualsearch.service.search;

import com.visualsearch.config.JwtProperties;
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
public class SearchCursorService {
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private final SecretKeySpec key;

    public SearchCursorService(JwtProperties properties) {
        key = new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String encode(UUID searchId, long offset, int size) {
        String payload = ENCODER.encodeToString(("search-v1:" + searchId + ":" + offset + ":" + size)
                .getBytes(StandardCharsets.UTF_8));
        return payload + "." + ENCODER.encodeToString(sign(payload));
    }

    public Cursor decode(String token, UUID searchId) {
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
            if (offset < 1 || offset > Long.MAX_VALUE - 101 || size < 1 || size > 100) {
                throw new IllegalArgumentException();
            }
            return new Cursor(offset, size);
        } catch (IllegalArgumentException exception) {
            throw new BadRequestException("Invalid search cursor");
        }
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
