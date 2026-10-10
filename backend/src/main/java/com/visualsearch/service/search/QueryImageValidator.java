package com.visualsearch.service.search;

import com.visualsearch.config.SearchProperties;
import com.visualsearch.enums.ImageFormat;
import com.visualsearch.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class QueryImageValidator {
    private final SearchProperties properties;

    public QueryImage validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("A non-empty query image is required");
        }
        if (file.getSize() > properties.getMaxImageBytes()) {
            throw new BadRequestException("Query image exceeds the configured size limit");
        }
        try (var input = file.getInputStream()) {
            byte[] bytes = input.readNBytes(properties.getMaxImageBytes() + 1);
            if (bytes.length > properties.getMaxImageBytes()) {
                throw new BadRequestException("Query image exceeds the configured size limit");
            }
            try (var imageInput = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(imageInput);
                if (!readers.hasNext()) {
                    throw new BadRequestException("Query image cannot be decoded");
                }
                var reader = readers.next();
                try {
                    reader.setInput(imageInput, true, true);
                    String formatName = reader.getFormatName().toUpperCase(Locale.ROOT);
                    if (!formatName.equals("JPEG") && !formatName.equals("PNG") && !formatName.equals("WEBP")) {
                        throw new BadRequestException("Only JPEG, PNG and WebP query images are supported");
                    }
                    ImageFormat format = ImageFormat.valueOf(formatName);
                    String contentType = "image/" + formatName.toLowerCase(Locale.ROOT);
                    if (!contentType.equalsIgnoreCase(file.getContentType())) {
                        throw new BadRequestException("Content type does not match the query image");
                    }
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    if (width < 1 || height < 1 || width > 10_000 || height > 10_000
                            || (long) width * height > properties.getMaxImagePixels()) {
                        throw new BadRequestException("Query image dimensions exceed the supported limit");
                    }
                    // Check dimensions before decoding to bound decompressed memory usage.
                    var decoded = reader.read(0);
                    if (decoded == null) {
                        throw new BadRequestException("Query image cannot be decoded");
                    }
                    decoded.flush();
                    return new QueryImage(bytes, format, contentType, width, height);
                } finally {
                    reader.dispose();
                }
            }
        } catch (IOException exception) {
            throw new BadRequestException("Query image cannot be read or decoded");
        }
    }

    public record QueryImage(byte[] bytes, ImageFormat format, String contentType, int width, int height) {
        public String filename() {
            return "query." + format.name().toLowerCase(Locale.ROOT);
        }
    }
}
