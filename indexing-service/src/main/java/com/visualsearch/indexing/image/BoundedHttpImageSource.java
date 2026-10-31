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

        var pending = httpClient.sendAsync(request, info -> {
            if (info.statusCode() != 200) throw classifyStatus(info.statusCode(), uri);
            long length = info.headers().firstValueAsLong("Content-Length").orElse(-1);
            if (length == 0 || length > properties.getMaxBytes()) {
                throw new PermanentImageException("Image Content-Length is empty or exceeds byte limit");
            }
            return new BoundedBodySubscriber(properties.getMaxBytes());
        });
        try {
            var response = pending.get(properties.getRequestTimeout().toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            byte[] bytes = response.body();
            if (bytes.length == 0) throw new PermanentImageException("Downloaded image contains 0 bytes");
            String contentType = response.headers().firstValue("Content-Type")
                    .map(value -> value.split(";", 2)[0].trim()).orElse("application/octet-stream");
            return new DownloadedImage(bytes, contentType);
        } catch (java.util.concurrent.TimeoutException exception) {
            throw new TransientDependencyException("Image download timed out for " + safeUri(uri), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransientDependencyException("Image download was interrupted", exception);
        } catch (java.util.concurrent.ExecutionException exception) {
            Throwable cause = exception.getCause();
            while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
                cause = cause.getCause();
            }
            if (cause instanceof PermanentImageException permanent) throw permanent;
            if (cause instanceof TransientDependencyException transientFailure) throw transientFailure;
            throw new TransientDependencyException("Image download failed for " + safeUri(uri), cause);
        } finally {
            pending.cancel(true);
        }
    }

    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private final long maxBytes;
        private long received;
        private java.util.concurrent.Flow.Subscription subscription;
        private boolean done;
        private BoundedBodySubscriber(long maxBytes) { this.maxBytes = maxBytes; }
        public java.util.concurrent.CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }
        public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
            if (done) return;
            for (var buffer : buffers) received += buffer.remaining();
            if (received > maxBytes) {
                done = true;
                subscription.cancel();
                delegate.onError(new PermanentImageException("Downloaded image exceeds byte limit " + maxBytes));
            } else delegate.onNext(buffers);
        }
        public void onError(Throwable failure) { if (!done) { done = true; delegate.onError(failure); } }
        public void onComplete() { if (!done) { done = true; delegate.onComplete(); } }
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

    private String safeUri(URI uri) {
        return uri.getScheme() + "://" + uri.getHost() + uri.getPath();
    }

}
