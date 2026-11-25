package com.visualsearch.indexing.image;

import com.visualsearch.indexing.exception.PermanentImageException;
import com.visualsearch.indexing.exception.TransientDependencyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
public class ClipFrameExtractor {
    private static final int GIF_SAMPLES = 240;
    private static final int MP4_SAMPLES = 60;
    private static final int MAX_REPRESENTATIVES = 8;
    private static final double MAX_SIMILAR_DIFFERENCE = 0.06;
    private static final Pattern TIMESTAMP = Pattern.compile("pts_time:([0-9.eE+-]+)");

    public record Frame(int timestampMs, byte[] jpeg) {}
    record Sample(Frame frame, int[] pixels) {}

    public List<Frame> extract(UUID clipId, byte[] bytes, boolean gif, int durationMs) {
        Path directory = null;
        long started = System.nanoTime();
        try {
            directory = Files.createTempDirectory("visual-clip-frames-");
            Path input = Files.write(directory.resolve(gif ? "source.gif" : "source.mp4"), bytes);
            Path report = directory.resolve("ffmpeg.log");
            int limit = gif ? GIF_SAMPLES : MP4_SAMPLES;
            // Select by presentation time so variable-rate clips and unequal GIF delays work.
            String select = "select='isnan(prev_selected_t)+gt(floor(t*2),floor(prev_selected_t*2))',";
            if (gif) {
                run(report, List.of("ffprobe", "-v", "error", "-max_alloc", "67108864",
                        "-protocol_whitelist", "file", "-f", "gif", "-count_packets", "-select_streams", "v:0",
                        "-show_entries", "stream=nb_read_packets", "-of", "csv=p=0", input.toString()));
                int packets = Integer.parseInt(Files.readString(report).strip());
                double sampleRate = GIF_SAMPLES * 1000.0 / durationMs;
                select = packets <= GIF_SAMPLES ? "" : String.format(Locale.ROOT,
                        "select='isnan(prev_selected_t)+gt(floor(t*%.9f),floor(prev_selected_t*%.9f))',",
                        sampleRate, sampleRate);
            }
            run(report, List.of("ffmpeg", "-hide_banner", "-nostdin", "-loglevel", "info", "-xerror",
                    "-max_alloc", "67108864", "-filter_threads", "1", "-threads", "1",
                    "-max_pixels", "8847360", "-protocol_whitelist", "file",
                    "-f", gif ? "gif" : "mov", "-i", input.toString(), "-map", "0:V:0",
                    "-vf", select + "scale=400:400:force_original_aspect_ratio=decrease,showinfo",
                    "-fps_mode", "passthrough", "-t", "30", "-frames:v", Integer.toString(limit),
                    "-an", "-c:v", "mjpeg", "-q:v", "4", "-threads", "1",
                    directory.resolve("frame-%03d.jpg").toString()));
            List<Integer> times;
            try (var lines = Files.lines(report)) {
                times = lines.filter(line -> line.contains("Parsed_showinfo"))
                        .flatMap(line -> TIMESTAMP.matcher(line).results())
                        .map(match -> (int) Math.round(Double.parseDouble(match.group(1)) * 1000))
                        .limit(limit).toList();
            }
            long decoded = System.nanoTime();
            List<Sample> samples = new ArrayList<>();
            for (int index = 0; index < times.size(); index++) {
                Path framePath = directory.resolve(String.format(Locale.ROOT, "frame-%03d.jpg", index + 1));
                if (!Files.isRegularFile(framePath)) break;
                int timestamp = Math.max(0, times.get(index));
                if (timestamp >= durationMs && index > 0) break;
                byte[] jpeg = Files.readAllBytes(framePath);
                samples.add(new Sample(new Frame(timestamp, jpeg), pixels(jpeg)));
            }
            if (samples.isEmpty()) throw new PermanentImageException("Clip has no decodable frame");
            List<Frame> frames = select(samples);
            log.info("Clip frame selection clipId={} sampled={} retained={} decodeMs={} selectionMs={}",
                    clipId, samples.size(), frames.size(), TimeUnit.NANOSECONDS.toMillis(decoded - started),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - decoded));
            return frames;
        } catch (NumberFormatException exception) {
            throw new PermanentImageException("Invalid clip frame metadata", exception);
        } catch (IOException exception) {
            throw new TransientDependencyException("Could not read temporary clip files or start FFmpeg", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TransientDependencyException("Clip decoding was interrupted", exception);
        } finally {
            if (directory != null) clean(directory);
        }
    }

    static List<Frame> select(List<Sample> samples) {
        List<Sample> scenes = new ArrayList<>();
        for (Sample sample : samples) {
            if (scenes.stream().noneMatch(scene ->
                    sample.frame().timestampMs() == scene.frame().timestampMs()
                    || difference(sample, scene) <= MAX_SIMILAR_DIFFERENCE)) {
                scenes.add(sample);
            }
        }
        List<Sample> representatives = new ArrayList<>();
        while (!scenes.isEmpty() && representatives.size() < MAX_REPRESENTATIVES) {
            Sample next = scenes.getFirst();
            if (!representatives.isEmpty()) {
                next = scenes.stream().max(Comparator.comparingDouble(scene -> representatives.stream()
                        .mapToDouble(existing -> difference(scene, existing)).min().orElseThrow())).orElseThrow();
            }
            representatives.add(next);
            scenes.remove(next);
        }
        return representatives.stream().map(Sample::frame)
                .sorted(Comparator.comparingInt(Frame::timestampMs)).toList();
    }

    private static double difference(Sample a, Sample b) {
        long total = 0;
        for (int index = 0; index < a.pixels().length; index++) {
            int x = a.pixels()[index];
            int y = b.pixels()[index];
            for (int shift = 0; shift < 24; shift += 8) {
                total += Math.abs(((x >> shift) & 255) - ((y >> shift) & 255));
            }
        }
        return total / (3.0 * 255 * a.pixels().length);
    }

    private static int[] pixels(byte[] jpeg) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
        if (image == null) throw new PermanentImageException("Decoded clip frame is invalid");
        BufferedImage small = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = small.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(image, 0, 0, 32, 32, null);
        } finally {
            graphics.dispose();
            image.flush();
        }
        return small.getRGB(0, 0, 32, 32, null, 0, 32);
    }

    private static void run(Path report, List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true)
                .redirectOutput(report.toFile()).start();
        try {
            if (!process.waitFor(25, TimeUnit.SECONDS) || process.exitValue() != 0) {
                throw new PermanentImageException("Clip could not be decoded within processing limits");
            }
        } finally {
            if (process.isAlive()) process.destroyForcibly().waitFor();
        }
    }

    private static void clean(Path directory) {
        try (var files = Files.newDirectoryStream(directory)) {
            for (Path file : files) Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Could not remove temporary clip frames", exception);
        }
        try {
            Files.deleteIfExists(directory);
        } catch (IOException exception) {
            log.warn("Could not remove temporary clip directory", exception);
        }
    }
}
