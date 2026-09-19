package com.afarrukh.giftools.video;

import com.afarrukh.giftools.gif.GifEncoder;
import com.afarrukh.giftools.gif.GifProcessor;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import org.jcodec.api.FrameGrab;
import org.jcodec.api.JCodecException;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.scale.AWTUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a short video clip into an animated GIF, so everything downstream of an upload only ever sees a GIF.
 *
 * <p>Video is decoded in process rather than through ffmpeg, which keeps the editor a single jar with nothing to
 * install. That buys portability at the cost of reach: MP4 carrying H.264 is what this handles, and anything else has
 * to be converted before it gets here.
 */
public final class VideoToGif {

    private static final Logger LOG = LoggerFactory.getLogger(VideoToGif.class);

    /** GIF delays are whole hundredths of a second, so there is little point sampling a clip much faster than this. */
    private static final int TARGET_FPS = 15;

    private static final int MAX_DIMENSION = 480;
    private static final int MAX_FRAMES = 300;
    private static final int MIN_DELAY_HUNDREDTHS = 2;

    /**
     * Frames come out of the decoder in the order they were coded, not the order they are shown: a B frame is decoded
     * after the frame it is displayed before. They are reordered through a buffer this many frames deep, comfortably
     * more than the four or so a real encoder reorders across.
     */
    private static final int REORDER_WINDOW = 16;

    private VideoToGif() {}

    /** Whether these bytes open an ISO base media container, which is what MP4, M4V and MOV all are. */
    public static boolean isMp4(byte[] data) {
        return data.length > 12 && data[4] == 'f' && data[5] == 't' && data[6] == 'y' && data[7] == 'p';
    }

    /** Whether these bytes open a Matroska container, which is what WebM is. */
    public static boolean isMatroska(byte[] data) {
        return data.length > 4
                && (data[0] & 0xFF) == 0x1A
                && (data[1] & 0xFF) == 0x45
                && (data[2] & 0xFF) == 0xDF
                && (data[3] & 0xFF) == 0xA3;
    }

    public static boolean isVideo(byte[] data) {
        return isMp4(data) || isMatroska(data);
    }

    /** One decoded frame, scaled down and waiting its turn to be put back into display order. */
    private record Decoded(double timestamp, double duration, BufferedImage image) {}

    /**
     * @param video an MP4 clip carrying H.264
     * @return the same animation as a GIF, sampled down to a size and frame rate a GIF can reasonably hold
     */
    public static byte[] convert(byte[] video) throws IOException {
        if (isMatroska(video)) {
            throw new IllegalArgumentException(
                    "That is a WebM video, which this editor cannot read. Try an MP4 or GIF version of it instead");
        }
        if (!isMp4(video)) {
            throw new IllegalArgumentException("That is neither a GIF nor an MP4 video");
        }

        var sampler = new Sampler();
        var pending = new PriorityQueue<Decoded>(Comparator.comparingDouble(Decoded::timestamp));
        var channel = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(video), video.length);
        try {
            var grab = FrameGrab.createFrameGrab(channel);
            for (var frame = grab.getNativeFrameWithMetadata();
                    frame != null && !sampler.full();
                    frame = grab.getNativeFrameWithMetadata()) {
                var image = AWTUtil.toBufferedImage(frame.getPicture(), frame.getOrientation());
                pending.add(new Decoded(
                        frame.getTimestamp(),
                        frame.getDuration(),
                        GifProcessor.fitWithin(image, MAX_DIMENSION)));
                if (pending.size() > REORDER_WINDOW) {
                    sampler.offer(pending.poll());
                }
            }
        } catch (JCodecException | RuntimeException e) {
            LOG.warn("Could not decode that video", e);
            throw new IllegalArgumentException("That video could not be decoded. Only MP4 carrying H.264 is supported");
        }
        while (!pending.isEmpty() && !sampler.full()) {
            sampler.offer(pending.poll());
        }
        return sampler.encode();
    }

    /** Thins the frames down to {@link #TARGET_FPS} as they arrive in display order, and encodes what is left. */
    private static final class Sampler {

        private static final double STEP = 1d / TARGET_FPS;

        private final List<BufferedImage> frames = new ArrayList<>();
        private final List<Double> times = new ArrayList<>();
        private double nextWanted;
        private double lastDuration = STEP;

        boolean full() {
            return frames.size() >= MAX_FRAMES;
        }

        void offer(Decoded frame) {
            // Anything arriving before the next slot is due is a frame the GIF has no room to show.
            if (frame.timestamp() < nextWanted - 1e-4) {
                return;
            }
            do {
                nextWanted += STEP;
            } while (nextWanted <= frame.timestamp());
            frames.add(frame.image());
            times.add(frame.timestamp());
            if (frame.duration() > 0) {
                lastDuration = frame.duration();
            }
        }

        byte[] encode() throws IOException {
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("That video had no frames this editor could read");
            }
            if (full()) {
                LOG.info("Video was longer than {} frames at {}fps, the rest was dropped", MAX_FRAMES, TARGET_FPS);
            }
            var delays = new ArrayList<Integer>(frames.size());
            for (int i = 0; i < frames.size(); i++) {
                double shownFor = i + 1 < times.size() ? times.get(i + 1) - times.get(i) : lastDuration;
                delays.add(Math.max(MIN_DELAY_HUNDREDTHS, (int) Math.round(shownFor * 100)));
            }
            return GifEncoder.encode(frames, delays, true);
        }
    }
}
