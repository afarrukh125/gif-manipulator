package com.afarrukh.giftools.video;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decodes a clip by handing it to an ffmpeg that is already on this machine.
 *
 * <p>The bundled decoder only reads H.264 in MP4. ffmpeg reads everything else - WebM above all, which carries VP8 or
 * VP9 and which no pure Java decoder handles - so it is preferred whenever it is installed.
 */
final class Ffmpeg {

    private static final Logger LOG = LoggerFactory.getLogger(Ffmpeg.class);

    private static final int TIMEOUT_SECONDS = 180;

    private static volatile Boolean available;

    private Ffmpeg() {}

    /** The binary to run: whatever {@code FFMPEG} names, or plain {@code ffmpeg} found on the path. */
    static String binary() {
        var configured = System.getenv("FFMPEG");
        return configured == null || configured.isBlank() ? "ffmpeg" : configured.trim();
    }

    /** Whether an ffmpeg can be run at all. Asked once and remembered, since starting a process to find out is slow. */
    static boolean available() {
        var known = available;
        if (known == null) {
            synchronized (Ffmpeg.class) {
                known = available;
                if (known == null) {
                    known = probe();
                    available = known;
                }
            }
        }
        return known;
    }

    private static boolean probe() {
        try {
            var process = new ProcessBuilder(binary(), "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException e) {
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Writes the clip out as still frames and reads them back.
     *
     * <p>The clip goes to a file rather than down ffmpeg's standard input because an MP4 keeps the index it needs to
     * start at either end of the file, and a pipe cannot be rewound to look for it.
     */
    static VideoToGif.Clip decode(byte[] video, int fps, int maxDimension, int maxFrames) throws IOException {
        var work = Files.createTempDirectory("giftools-");
        try {
            var input = work.resolve("input");
            Files.write(input, video);
            run(input, work, fps, maxDimension, maxFrames);

            var frames = new ArrayList<BufferedImage>();
            for (var file : framesIn(work)) {
                var frame = ImageIO.read(file.toFile());
                if (frame != null) {
                    frames.add(frame);
                }
            }
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("That video had no frames this editor could read");
            }
            return new VideoToGif.Clip(frames, evenDelays(frames.size(), fps));
        } finally {
            deleteTree(work);
        }
    }

    private static void run(Path input, Path work, int fps, int maxDimension, int maxFrames) throws IOException {
        // force_original_aspect_ratio=decrease fits the frame inside a square box without ever enlarging it, which is
        // the same "shrink the longest side to this" rule the bundled decoder applies.
        var filter = "fps=%d,scale='min(%d,iw)':'min(%d,ih)':force_original_aspect_ratio=decrease:flags=lanczos"
                .formatted(fps, maxDimension, maxDimension);
        var command = List.of(
                binary(),
                "-nostdin",
                "-v", "error",
                "-y",
                "-i", input.toString(),
                "-an",
                "-vf", filter,
                "-frames:v", Integer.toString(maxFrames),
                work.resolve("%05d.png").toString());

        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not run " + binary() + ": " + e.getMessage(), e);
        }
        String complaints;
        try (var output = process.getInputStream()) {
            complaints = new String(output.readAllBytes()).trim();
        }
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalArgumentException("That video took too long to convert");
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("That conversion was interrupted", e);
        }
        if (process.exitValue() != 0) {
            // What ffmpeg prints is usually a knock-on complaint from further down the filter graph rather than the
            // reason, so the whole thing goes to the log and the person gets the two explanations worth acting on.
            LOG.warn("ffmpeg exited with {}: {}", process.exitValue(), complaints);
            throw new IllegalArgumentException("ffmpeg could not decode that video. Either the file is damaged, or"
                    + " this ffmpeg was built without the codec it uses - VP9 and AV1 are missing from older builds");
        }
    }

    private static List<Path> framesIn(Path work) throws IOException {
        try (Stream<Path> files = Files.list(work)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".png"))
                    .sorted(Comparator.comparing(Path::getFileName))
                    .toList();
        }
    }

    /**
     * ffmpeg hands back frames at an even rate, but a hundredth of a second does not divide evenly into most of them.
     * Taking each delay as the gap between two rounded running totals spreads the remainder out, so the animation as a
     * whole keeps time rather than drifting slower with every frame.
     */
    private static List<Integer> evenDelays(int count, int fps) {
        var delays = new ArrayList<Integer>(count);
        for (int i = 0; i < count; i++) {
            int elapsed = Math.round((i + 1) * 100f / fps);
            int previous = Math.round(i * 100f / fps);
            delays.add(Math.max(VideoToGif.MIN_DELAY_HUNDREDTHS, elapsed - previous));
        }
        return delays;
    }

    private static void deleteTree(Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException | UncheckedIOException e) {
            LOG.warn("Could not clean up {}", root, e);
        }
    }
}
