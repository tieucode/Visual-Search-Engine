package com.visualsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.visualsearch.enums.ImageFormat;
import com.visualsearch.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class ClipUploadProbe {
    private static final BigDecimal MAX_SECONDS = BigDecimal.valueOf(30);
    private final Semaphore slots = new Semaphore(2, true);
    private final ObjectMapper mapper;

    public Clip inspect(MultipartFile file, ImageFormat format) throws IOException, InterruptedException {
        boolean gif = format == ImageFormat.GIF;
        String mime = gif ? "image/gif" : "video/mp4";
        if (!mime.equalsIgnoreCase(file.getContentType())) {
            throw new BadRequestException("File content type does not match its extension");
        }
        slots.acquire();
        Path input = null;
        Path output = null;
        try {
            input = Files.createTempFile("visual-clip-", gif ? ".gif" : ".mp4");
            output = Files.createTempFile("visual-clip-output-", ".tmp");
            try (var content = file.getInputStream()) {
                Files.copy(content, input, StandardCopyOption.REPLACE_EXISTING);
            }
            String demuxer = gif ? "gif" : "mov";
            var probe = new ArrayList<>(List.of("ffprobe", "-v", "error", "-max_alloc", "67108864",
                    "-protocol_whitelist", "file", "-f", demuxer, "-select_streams", "V:0"));
            if (gif) probe.add("-count_packets");
            probe.addAll(List.of("-show_entries",
                    "stream=codec_name,width,height,duration,nb_read_packets:stream_side_data=rotation:format=duration:format_tags=major_brand",
                    "-of", "json", input.toString()));
            run(output, probe);
            var root = mapper.readTree(Files.readAllBytes(output));
            var stream = root.path("streams").path(0);
            int duration = durationMs(root.path("format").path("duration"), stream.path("duration"),
                    gif && stream.path("nb_read_packets").asInt() == 1);
            int width = stream.path("width").asInt();
            int height = stream.path("height").asInt();
            if (!stream.path("codec_name").asText().equals(gif ? "gif" : "h264")
                    || (!gif && !isMp4(root.path("format").path("tags").path("major_brand").asText()))
                    || width < 1 || height < 1 || width > 4096 || height > 4096
                    || (long) width * height > 4096L * 2160) {
                throw new BadRequestException("Use a GIF or an H.264 MP4 within the supported dimensions");
            }
            for (JsonNode data : stream.path("side_data_list")) {
                if (Math.floorMod(data.path("rotation").asInt(), 180) == 90) {
                    int codedWidth = width;
                    width = height;
                    height = codedWidth;
                    break;
                }
            }
            run(output, List.of("ffmpeg", "-v", "error", "-xerror", "-nostdin",
                    "-max_alloc", "67108864", "-filter_threads", "1", "-threads", "1",
                    "-protocol_whitelist", "file", "-f", demuxer, "-i", input.toString(),
                    "-map", "0:V:0", "-frames:v", "1", "-vf",
                    "scale=400:400:force_original_aspect_ratio=decrease", "-threads", "1",
                    "-c:v", "mjpeg", "-f", "image2pipe", "pipe:1"));
            byte[] cover = Files.readAllBytes(output);
            if (cover.length == 0) throw new BadRequestException("Clip has no decodable frame");
            return new Clip(width, height, duration, cover);
        } finally {
            try {
                if (input != null) Files.deleteIfExists(input);
            } finally {
                slots.release();
                if (output != null) Files.deleteIfExists(output);
            }
        }
    }

    static int durationMs(JsonNode container, JsonNode stream, boolean singleGifFrame) {
        BigDecimal duration = seconds(container).max(seconds(stream));
        // A static GIF can omit a delay entirely.
        if (duration.signum() == 0 && singleGifFrame) return 100;
        if (duration.signum() <= 0 || duration.compareTo(MAX_SECONDS) > 0) {
            throw new BadRequestException("Clip duration must be greater than zero and at most 30 seconds");
        }
        return duration.movePointRight(3).setScale(0, RoundingMode.CEILING).intValueExact();
    }

    private static BigDecimal seconds(JsonNode value) {
        try {
            return new BigDecimal(value.asText());
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private static boolean isMp4(String brand) {
        return brand.startsWith("iso") || brand.startsWith("mp4")
                || List.of("avc1", "M4V ", "MSNV", "dash", "cmfc", "cmfs").contains(brand);
    }

    private void run(Path output, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectOutput(output.toFile())
                .redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try {
            if (!process.waitFor(12, TimeUnit.SECONDS) || process.exitValue() != 0
                    || Files.size(output) > 1_048_576L) {
                throw new BadRequestException("Clip could not be decoded within processing limits");
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
        }
    }

    public record Clip(int width, int height, int durationMs, byte[] cover) {}
}
