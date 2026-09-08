package com.afarrukh.giftools.gif;

import at.dhyan.open_imaging.GifDecoder;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

/** Decodes a GIF, applies a set of {@link GifOptions} to it and encodes the result. */
public final class GifProcessor {

    public static final int MAX_DIMENSION = 2000;

    /** Renderers substitute a tenth of a second for delays shorter than this, so a smaller one would slow the GIF. */
    private static final int MIN_DELAY_HUNDREDTHS = 2;

    private GifProcessor() {}

    /** What an uploaded GIF contains, before any modification. */
    public record Meta(int width, int height, int frameCount, int durationMs, int sizeBytes) {}

    /** A rendered GIF and the shape of what came out, which the editor reports back to the user. */
    public record Result(byte[] data, int width, int height, int frameCount, int durationMs) {}

    public static Meta inspect(byte[] source) throws IOException {
        var image = GifDecoder.read(source);
        int duration = 0;
        for (int i = 0; i < image.getFrameCount(); i++) {
            duration += image.getDelay(i) * 10;
        }
        return new Meta(image.getWidth(), image.getHeight(), image.getFrameCount(), duration, source.length);
    }

    public static Result process(byte[] source, GifOptions options) throws IOException {
        var image = GifDecoder.read(source);
        int total = image.getFrameCount();

        var order = selectFrames(image, options, total);
        if (order.isEmpty()) {
            throw new IllegalArgumentException("That selection leaves no frames to render");
        }

        var delays = new ArrayList<Integer>(order.size());
        int duration = 0;
        for (var frame : order) {
            int delay = delayFor(frame.delayHundredths(), options);
            delays.add(delay);
            duration += delay * 10;
        }

        var rendered = new HashMap<Integer, BufferedImage>();
        var frames = new ArrayList<BufferedImage>(order.size());
        for (var frame : order) {
            frames.add(rendered.computeIfAbsent(frame.index(), i -> transform(image.getFrame(i), options)));
        }

        var first = frames.getFirst();
        return new Result(
                GifEncoder.encode(frames, delays, options.loop()),
                first.getWidth(),
                first.getHeight(),
                frames.size(),
                duration);
    }

    /** One frame of the output: which source frame to draw, and how long it should be shown. */
    private record Selected(int index, int delayHundredths) {}

    private static List<Selected> selectFrames(GifDecoder.GifImage image, GifOptions options, int total) {
        int end = options.endFrame() < 0 || options.endFrame() >= total ? total - 1 : options.endFrame();
        int start = Math.min(options.startFrame(), end);

        var selected = new ArrayList<Selected>();
        for (int i = start; i <= end; i += options.frameStep()) {
            // A dropped frame still took up time, so its delay is folded into the frame that replaces it. Without this
            // the animation would run faster the more frames you skipped.
            int delay = 0;
            for (int j = i; j <= Math.min(i + options.frameStep() - 1, end); j++) {
                delay += image.getDelay(j);
            }
            selected.add(new Selected(i, delay));
        }
        if (selected.isEmpty()) {
            return selected;
        }

        Collections.rotate(selected, -(options.startOffset() % selected.size()));
        if (options.reverse()) {
            Collections.reverse(selected);
        }
        if (options.boomerang() && selected.size() > 2) {
            for (int i = selected.size() - 2; i >= 1; i--) {
                selected.add(selected.get(i));
            }
        }
        return selected;
    }

    private static int delayFor(int sourceDelayHundredths, GifOptions options) {
        int base = options.frameDelayMs() > 0 ? Math.round(options.frameDelayMs() / 10f) : sourceDelayHundredths;
        return Math.max(MIN_DELAY_HUNDREDTHS, Math.round((float) (base / options.speed())));
    }

    private static BufferedImage transform(BufferedImage source, GifOptions options) {
        var image = crop(source, options);
        image = rotate(image, options.rotation());
        image = flip(image, options.flipHorizontal(), options.flipVertical());
        image = resize(image, options.width(), options.height());
        return options.adjustsColour() ? adjustColour(image, options) : image;
    }

    private static BufferedImage crop(BufferedImage source, GifOptions options) {
        if (options.cropWidth() <= 0 || options.cropHeight() <= 0) {
            return source;
        }
        int x = Math.min(options.cropX(), source.getWidth() - 1);
        int y = Math.min(options.cropY(), source.getHeight() - 1);
        int w = Math.min(options.cropWidth(), source.getWidth() - x);
        int h = Math.min(options.cropHeight(), source.getHeight() - y);
        if (w <= 0 || h <= 0) {
            return source;
        }
        var cropped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var graphics = cropped.createGraphics();
        graphics.drawImage(source, 0, 0, w, h, x, y, x + w, y + h, null);
        graphics.dispose();
        return cropped;
    }

    private static BufferedImage rotate(BufferedImage source, int degrees) {
        if (degrees == 0) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        boolean quarterTurn = degrees != 180;
        var rotated = new BufferedImage(quarterTurn ? h : w, quarterTurn ? w : h, BufferedImage.TYPE_INT_ARGB);
        var row = new int[w];
        for (int y = 0; y < h; y++) {
            source.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                switch (degrees) {
                    case 90 -> rotated.setRGB(h - 1 - y, x, row[x]);
                    case 180 -> rotated.setRGB(w - 1 - x, h - 1 - y, row[x]);
                    default -> rotated.setRGB(y, w - 1 - x, row[x]);
                }
            }
        }
        return rotated;
    }

    private static BufferedImage flip(BufferedImage source, boolean horizontal, boolean vertical) {
        if (!horizontal && !vertical) {
            return source;
        }
        int w = source.getWidth();
        int h = source.getHeight();
        var flipped = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var graphics = flipped.createGraphics();
        int x1 = horizontal ? w : 0;
        int x2 = horizontal ? 0 : w;
        int y1 = vertical ? h : 0;
        int y2 = vertical ? 0 : h;
        graphics.drawImage(source, x1, y1, x2, y2, 0, 0, w, h, null);
        graphics.dispose();
        return flipped;
    }

    private static BufferedImage resize(BufferedImage source, int requestedWidth, int requestedHeight) {
        int w = source.getWidth();
        int h = source.getHeight();
        int targetWidth = requestedWidth;
        int targetHeight = requestedHeight;
        if (targetWidth <= 0 && targetHeight <= 0) {
            return source;
        }
        if (targetWidth <= 0) {
            targetWidth = Math.max(1, Math.round(w * (targetHeight / (float) h)));
        } else if (targetHeight <= 0) {
            targetHeight = Math.max(1, Math.round(h * (targetWidth / (float) w)));
        }
        targetWidth = Math.min(targetWidth, MAX_DIMENSION);
        targetHeight = Math.min(targetHeight, MAX_DIMENSION);
        if (targetWidth == w && targetHeight == h) {
            return source;
        }

        // Halving repeatedly before the final step keeps a big downscale from aliasing, which one bilinear pass to a
        // much smaller size would do.
        var current = source;
        while (current.getWidth() > targetWidth * 2 && current.getHeight() > targetHeight * 2) {
            current = drawScaled(
                    current,
                    Math.max(targetWidth, current.getWidth() / 2),
                    Math.max(targetHeight, current.getHeight() / 2));
        }
        return drawScaled(current, targetWidth, targetHeight);
    }

    private static BufferedImage drawScaled(BufferedImage source, int width, int height) {
        var scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var graphics = scaled.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(
                RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return scaled;
    }

    private static BufferedImage adjustColour(BufferedImage source, GifOptions options) {
        double saturation = options.grayscale() ? 0 : options.saturation();
        var curve = toneCurve(options);

        int w = source.getWidth();
        int h = source.getHeight();
        var adjusted = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var row = new int[w];
        for (int y = 0; y < h; y++) {
            source.getRGB(0, y, w, 1, row, 0, w);
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (saturation != 1) {
                    double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                    r = clampChannel(luma + (r - luma) * saturation);
                    g = clampChannel(luma + (g - luma) * saturation);
                    b = clampChannel(luma + (b - luma) * saturation);
                }
                row[x] = (argb & 0xFF000000) | curve[r] << 16 | curve[g] << 8 | curve[b];
            }
            adjusted.setRGB(0, y, w, 1, row, 0, w);
        }
        return adjusted;
    }

    /** Brightness, contrast and inversion all act on one channel at a time, so they collapse into a lookup table. */
    private static int[] toneCurve(GifOptions options) {
        var curve = new int[256];
        for (int i = 0; i < curve.length; i++) {
            double value = i * options.brightness();
            value = (value - 128) * options.contrast() + 128;
            if (options.invert()) {
                value = 255 - value;
            }
            curve[i] = clampChannel(value);
        }
        return curve;
    }

    private static int clampChannel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }
}
