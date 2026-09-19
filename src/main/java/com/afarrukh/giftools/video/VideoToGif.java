package com.afarrukh.giftools.video;

import com.afarrukh.giftools.gif.GifEncoder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns a short video clip into an animated GIF, so everything downstream of an upload only ever sees a GIF.
 *
 * <p>There are two ways in. An ffmpeg already installed on the machine is used when there is one, because it reads
 * every format a link is likely to hand over. Without one the clip is decoded in process, which needs nothing
 * installed but only understands H.264 inside an MP4.
 */
public final class VideoToGif {

    private static final Logger LOG = LoggerFactory.getLogger(VideoToGif.class);

    /** GIF delays are whole hundredths of a second, so there is little point sampling a clip much faster than this. */
    private static final int TARGET_FPS = 15;

    private static final int MAX_DIMENSION = 480;
    private static final int MAX_FRAMES = 300;

    static final int MIN_DELAY_HUNDREDTHS = 2;

    private VideoToGif() {}

    /** A decoded clip: one complete image per frame, and how long each is shown in hundredths of a second. */
    record Clip(List<BufferedImage> frames, List<Integer> delays) {}

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

    private static boolean isAvi(byte[] data) {
        return data.length > 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'A'
                && data[9] == 'V'
                && data[10] == 'I';
    }

    public static boolean isVideo(byte[] data) {
        return isMp4(data) || isMatroska(data) || isAvi(data);
    }

    /** Whether a clip in this format can be read at all, which for anything but MP4 means having ffmpeg installed. */
    public static boolean canConvert(byte[] data) {
        return isMp4(data) || (isVideo(data) && Ffmpeg.available());
    }

    /** Whether WebM and the other formats only ffmpeg can read are available on this machine. */
    public static boolean hasFullCodecSupport() {
        return Ffmpeg.available();
    }

    /**
     * @param video a clip in one of the containers {@link #isVideo} recognises
     * @return the same animation as a GIF, sampled down to a size and frame rate a GIF can reasonably hold
     */
    public static byte[] convert(byte[] video) throws IOException {
        if (!isVideo(video)) {
            throw new IllegalArgumentException("That is neither a GIF nor a video this editor recognises");
        }
        if (!canConvert(video)) {
            throw new IllegalArgumentException((isMatroska(video) ? "Reading a WebM" : "Reading a video of that kind")
                    + " needs ffmpeg installed and on your PATH."
                    + " Install it, or use an MP4 or GIF version of the clip instead");
        }
        var clip = decode(video);
        if (clip.frames().size() >= MAX_FRAMES) {
            LOG.info("Clip was longer than {} frames at {}fps, the rest was dropped", MAX_FRAMES, TARGET_FPS);
        }
        return GifEncoder.encode(clip.frames(), clip.delays(), true);
    }

    private static Clip decode(byte[] video) throws IOException {
        if (!Ffmpeg.available()) {
            return JCodecClip.decode(video, TARGET_FPS, MAX_DIMENSION, MAX_FRAMES);
        }
        try {
            return Ffmpeg.decode(video, TARGET_FPS, MAX_DIMENSION, MAX_FRAMES);
        } catch (IOException | RuntimeException e) {
            if (!isMp4(video)) {
                throw e;
            }
            // An ffmpeg that is installed but too old, or built without the decoder this clip needs, should not leave
            // anyone worse off than having none at all, so an MP4 it choked on still gets the bundled decoder's turn.
            LOG.warn("ffmpeg could not convert that MP4, falling back to the bundled decoder", e);
            return JCodecClip.decode(video, TARGET_FPS, MAX_DIMENSION, MAX_FRAMES);
        }
    }
}
