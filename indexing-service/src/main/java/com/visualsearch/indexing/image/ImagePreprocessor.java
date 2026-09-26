package com.visualsearch.indexing.image;

import com.visualsearch.indexing.config.IndexingProperties;
import com.visualsearch.indexing.exception.PermanentImageException;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class ImagePreprocessor {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("JPEG", "JPG", "PNG", "WEBP", "GIF", "BMP");
    private static final float JPEG_QUALITY = 0.90f;

    private final IndexingProperties.Image properties;

    public ImagePreprocessor(IndexingProperties indexingProperties) {
        this.properties = indexingProperties.getImage();
        ImageIO.scanForPlugins();
    }

    public PreparedImage preprocess(UUID imageId, DownloadedImage downloaded) {
        byte[] inputBytes = downloaded.bytes();
        if (inputBytes == null || inputBytes.length == 0) {
            throw new PermanentImageException("Image contains 0 bytes");
        }

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(inputBytes))) {
            if (input == null) {
                throw new PermanentImageException("Could not create image decoder input");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new PermanentImageException("Data is not a supported image");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toUpperCase(Locale.ROOT);
                if (!SUPPORTED_FORMATS.contains(format)) {
                    throw new PermanentImageException("Unsupported image format: " + format);
                }

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                validateDimensions(width, height);

                BufferedImage decoded;
                try {
                    decoded = reader.read(0);
                } catch (IOException | RuntimeException exception) {
                    throw new PermanentImageException("Image is corrupted or cannot be decoded", exception);
                }
                if (decoded == null) {
                    throw new PermanentImageException("Image decoder returned no pixels");
                }

                BufferedImage normalized = resizeAndConvertToRgb(decoded);
                byte[] output = encodeJpeg(normalized);
                return new PreparedImage(
                        imageId,
                        output,
                        "image/jpeg",
                        normalized.getWidth(),
                        normalized.getHeight());
            } finally {
                reader.dispose();
            }
        } catch (PermanentImageException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new PermanentImageException("Image is invalid or corrupted", exception);
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new PermanentImageException("Image dimensions must be greater than zero");
        }
        long pixels;
        try {
            pixels = Math.multiplyExact((long) width, (long) height);
        } catch (ArithmeticException exception) {
            throw new PermanentImageException("Image dimensions overflow the supported range", exception);
        }
        if (pixels > properties.getMaxPixels()) {
            throw new PermanentImageException(
                    "Image has " + pixels + " pixels, exceeding limit " + properties.getMaxPixels());
        }
    }

    private BufferedImage resizeAndConvertToRgb(BufferedImage source) {
        int longest = Math.max(source.getWidth(), source.getHeight());
        double scale = longest > properties.getMaxDimension()
                ? (double) properties.getMaxDimension() / longest
                : 1.0;
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(java.awt.Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new PermanentImageException("JPEG encoder is not available");
        }
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam parameters = writer.getDefaultWriteParam();
            if (parameters.canWriteCompressed()) {
                parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                parameters.setCompressionQuality(JPEG_QUALITY);
            }
            writer.write(null, new IIOImage(image, null, null), parameters);
            imageOutput.flush();
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                throw new PermanentImageException("Preprocessing produced an empty image");
            }
            return bytes;
        } catch (IOException exception) {
            throw new PermanentImageException("Could not encode preprocessed image", exception);
        } finally {
            writer.dispose();
        }
    }
}
