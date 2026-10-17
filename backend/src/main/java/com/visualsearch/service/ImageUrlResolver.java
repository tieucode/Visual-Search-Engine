package com.visualsearch.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.function.Function;
import java.util.regex.Pattern;

/** Image locations may be existing MinIO object keys or absolute HTTP(S) URLs. */
@Service
@RequiredArgsConstructor
public class ImageUrlResolver {
    private static final Pattern URI_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:.*", Pattern.DOTALL);
    private final MinioStorageService storage;

    public String imageUrl(String location) {
        return resolve(location, storage::getPublicImageUrl);
    }

    public String thumbnailUrl(String location, String imageUrl) {
        String thumbnail = resolve(location, storage::getPresignedThumbnailUrl);
        return thumbnail == null ? imageUrl : thumbnail;
    }

    private String resolve(String location, Function<String, String> signObjectKey) {
        if (location == null || location.isBlank()) return null;
        if (URI_SCHEME.matcher(location).matches()) {
            try {
                URI uri = URI.create(location);
                boolean http = "https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme());
                if (!http || uri.getHost() == null || uri.getUserInfo() != null) return null;
                // Preserve query parameters, encoding and signatures; never fetch external URLs here.
                return location;
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
        if (location.startsWith("//")) return null;
        return signObjectKey.apply(location);
    }
}
