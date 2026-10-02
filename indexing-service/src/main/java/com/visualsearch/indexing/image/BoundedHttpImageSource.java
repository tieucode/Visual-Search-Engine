package com.visualsearch.indexing.image;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.PermanentImageException;
import com.visualsearch.indexing.exception.TransientDependencyException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BoundedHttpImageSource {

    private final IndexingProperties.Image properties;
    private final HttpClient httpClient;
    private final Set<String> allowedHosts;

    public BoundedHttpImageSource(IndexingProperties indexingProperties) {
        this.properties = indexingProperties.getImage();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.allowedHosts = properties.getAllowedHosts().stream()
                .map(host -> host.toLowerCase(Locale.ROOT).trim())
                .filter(host -> !host.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public DownloadedImage download(String imageUrl) {
        URI uri = validateUri(imageUrl);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(properties.getRequestTimeout())
                .header("Accept", "image/*")
                .GET()
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            int status = response.statusCode();
            if (status != 200) {
                closeQuietly(response.body());
                throw classifyStatus(status, uri);
            }

            long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (declaredLength == 0) {
                closeQuietly(response.body());
                throw new PermanentImageException("Image response is empty: " + safeUri(uri));
            }
            if (declaredLength > properties.getMaxBytes()) {
                closeQuietly(response.body());
                throw new PermanentImageException(
                        "Image Content-Length " + declaredLength + " exceeds limit " + properties.getMaxBytes());
            }

            byte[] bytes;
            try (InputStream input = response.body()) {
                bytes = readBounded(input, properties.getMaxBytes());
            }
            if (bytes.length == 0) {
                throw new PermanentImageException("Downloaded image contains 0 bytes: " + safeUri(uri));
            }
            String contentType = response.headers().firstValue("Content-Type")
                    .map(value -> value.split(";", 2)[0].trim())
                    .orElse("application/octet-stream");
            return new DownloadedImage(bytes, contentType);
        } catch (PermanentImageException | TransientDependencyException exception) {
            throw exception;
        } catch (HttpTimeoutException | ConnectException exception) {
            throw new TransientDependencyException("Timed out connecting to image source " + safeUri(uri), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransientDependencyException("Image download was interrupted", exception);
        } catch (IOException exception) {
            throw new TransientDependencyException("Image download failed for " + safeUri(uri), exception);
        }
    }

    private URI validateUri(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new PermanentImageException("imageUrl is required");
        }
        final URI uri;
        try {
            uri = URI.create(imageUrl);
        } catch (IllegalArgumentException exception) {
            throw new PermanentImageException("imageUrl is invalid", exception);
        }
        String scheme = uri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new PermanentImageException("Unsupported image URL scheme: " + scheme);
        }
        if (uri.getHost() == null) {
            throw new PermanentImageException("imageUrl must contain a host");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHosts.contains("*") && !allowedHosts.contains(host)) {
            throw new PermanentImageException("Image URL host is not allowed: " + host);
        }
        return uri;
    }

    private RuntimeException classifyStatus(int status, URI uri) {
        String message = "Image source returned HTTP " + status + " for " + safeUri(uri);
        if (status == 408 || status == 425 || status == 429 || status >= 500) {
            return new TransientDependencyException(message);
        }
        return new PermanentImageException(message);
    }

    private byte[] readBounded(InputStream input, long maxBytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new PermanentImageException("Downloaded image exceeds limit " + maxBytes + " bytes");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String safeUri(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
    }

    private void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
            // The response is already unusable; there is nothing else to recover here.
        }
    }
}
