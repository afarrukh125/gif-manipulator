package com.afarrukh.giftools.gif;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Every modification the editor can apply to one GIF.
 *
 * <p>The boxed components are the ones whose sensible default is not the zero value, so an absent field in the request
 * body can be told apart from one the caller set deliberately.
 *
 * @param reverse play the selected frames back to front
 * @param boomerang play forwards then backwards, without repeating the two end frames
 * @param loop repeat forever rather than playing once, defaults to true
 * @param startFrame first frame of the selection, counting from 0
 * @param endFrame last frame of the selection, defaults to the final frame
 * @param frameStep keep every nth frame, dropping the rest
 * @param startOffset rotate the selection so this position plays first
 * @param speed playback multiplier, 2 plays twice as fast
 * @param frameDelayMs a fixed delay for every frame, or 0 to keep the delays the source recorded
 * @param width output width in pixels, or 0 to derive it from the height
 * @param height output height in pixels, or 0 to derive it from the width
 * @param cropWidth crop width, or 0 for no crop
 * @param rotation clockwise rotation, one of 0, 90, 180 or 270
 * @param brightness channel multiplier where 1 leaves the frame alone
 * @param contrast spread around mid grey where 1 leaves the frame alone
 * @param saturation colourfulness where 1 leaves the frame alone and 0 is grey
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GifOptions(
        boolean reverse,
        boolean boomerang,
        Boolean loop,
        int startFrame,
        Integer endFrame,
        int frameStep,
        int startOffset,
        Double speed,
        int frameDelayMs,
        int width,
        int height,
        int cropX,
        int cropY,
        int cropWidth,
        int cropHeight,
        int rotation,
        boolean flipHorizontal,
        boolean flipVertical,
        boolean grayscale,
        boolean invert,
        Double brightness,
        Double contrast,
        Double saturation) {

    public GifOptions {
        loop = loop == null || loop;
        endFrame = endFrame == null ? -1 : endFrame;
        startFrame = Math.max(0, startFrame);
        frameStep = Math.max(1, frameStep);
        startOffset = Math.max(0, startOffset);
        speed = clamp(speed, 0.05, 20);
        frameDelayMs = Math.max(0, Math.min(frameDelayMs, 60_000));
        width = Math.max(0, Math.min(width, GifProcessor.MAX_DIMENSION));
        height = Math.max(0, Math.min(height, GifProcessor.MAX_DIMENSION));
        cropX = Math.max(0, cropX);
        cropY = Math.max(0, cropY);
        cropWidth = Math.max(0, cropWidth);
        cropHeight = Math.max(0, cropHeight);
        rotation = normaliseRotation(rotation);
        brightness = clamp(brightness, 0, 4);
        contrast = clamp(contrast, 0, 4);
        saturation = clamp(saturation, 0, 4);
    }

    public static GifOptions defaults() {
        return new GifOptions(
                false, false, true, 0, -1, 1, 0, 1.0, 0, 0, 0, 0, 0, 0, 0, 0, false, false, false, false, 1.0, 1.0,
                1.0);
    }

    boolean adjustsColour() {
        return grayscale || invert || brightness != 1 || contrast != 1 || saturation != 1;
    }

    private static double clamp(Double value, double min, double max) {
        return value == null ? 1 : Math.max(min, Math.min(max, value));
    }

    private static int normaliseRotation(int degrees) {
        int wrapped = ((degrees % 360) + 360) % 360;
        return wrapped % 90 == 0 ? wrapped : 0;
    }
}
