package com.afarrukh.giftools.video;

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
 * Decodes a clip in process, with no ffmpeg to install. That portability is the whole point of it, and the price is
 * reach: H.264 inside an MP4 is all this reads.
 */
final class JCodecClip {

    private static final Logger LOG = LoggerFactory.getLogger(JCodecClip.class);

    /**
     * Frames come out of the decoder in the order they were coded, not the order they are shown: a B frame is decoded
     * after the frame it is displayed before. They are reordered through a buffer this many frames deep, comfortably
     * more than the four or so a real encoder reorders across.
     */
    private static final int REORDER_WINDOW = 16;

    private JCodecClip() {}

    /** One decoded frame, scaled down and waiting its turn to be put back into display order. */
    private record Decoded(double timestamp, double duration, BufferedImage image) {}

    static VideoToGif.Clip decode(byte[] video, int fps, int maxDimension, int maxFrames) {
        var sampler = new Sampler(fps, maxFrames);
        var pending = new PriorityQueue<Decoded>(Comparator.comparingDouble(Decoded::timestamp));
        var channel = new ByteBufferSeekableByteChannel(ByteBuffer.wrap(video), video.length);
        try {
            var grab = FrameGrab.createFrameGrab(channel);
            for (var frame = grab.getNativeFrameWithMetadata();
                    frame != null && !sampler.full();
                    frame = grab.getNativeFrameWithMetadata()) {
                var image = AWTUtil.toBufferedImage(frame.getPicture(), frame.getOrientation());
                pending.add(new Decoded(
                        frame.getTimestamp(), frame.getDuration(), GifProcessor.fitWithin(image, maxDimension)));
                if (pending.size() > REORDER_WINDOW) {
                    sampler.offer(pending.poll());
                }
            }
        } catch (IOException | JCodecException | RuntimeException e) {
            LOG.warn("Could not decode that video", e);
            // An ffmpeg on the machine has already had its turn by this point, so pointing at one is only worth doing
            // when there is none to have tried.
            throw new IllegalArgumentException("That video could not be decoded, it may be damaged"
                    + (Ffmpeg.available() ? "" : ". Without ffmpeg installed, only MP4 carrying H.264 can be read"));
        }
        while (!pending.isEmpty() && !sampler.full()) {
            sampler.offer(pending.poll());
        }
        return sampler.clip();
    }

    /** Thins the frames down to the target rate as they arrive in display order. */
    private static final class Sampler {

        private final List<BufferedImage> frames = new ArrayList<>();
        private final List<Double> times = new ArrayList<>();
        private final double step;
        private final int maxFrames;
        private double nextWanted;
        private double lastDuration;

        Sampler(int fps, int maxFrames) {
            this.step = 1d / fps;
            this.maxFrames = maxFrames;
            this.lastDuration = step;
        }

        boolean full() {
            return frames.size() >= maxFrames;
        }

        void offer(Decoded frame) {
            // Anything arriving before the next slot is due is a frame the GIF has no room to show.
            if (frame.timestamp() < nextWanted - 1e-4) {
                return;
            }
            do {
                nextWanted += step;
            } while (nextWanted <= frame.timestamp());
            frames.add(frame.image());
            times.add(frame.timestamp());
            if (frame.duration() > 0) {
                lastDuration = frame.duration();
            }
        }

        VideoToGif.Clip clip() {
            if (frames.isEmpty()) {
                throw new IllegalArgumentException("That video had no frames this editor could read");
            }
            var delays = new ArrayList<Integer>(frames.size());
            for (int i = 0; i < frames.size(); i++) {
                double shownFor = i + 1 < times.size() ? times.get(i + 1) - times.get(i) : lastDuration;
                delays.add(Math.max(VideoToGif.MIN_DELAY_HUNDREDTHS, (int) Math.round(shownFor * 100)));
            }
            return new VideoToGif.Clip(frames, delays);
        }
    }
}
