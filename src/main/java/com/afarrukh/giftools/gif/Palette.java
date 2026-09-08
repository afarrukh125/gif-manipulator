package com.afarrukh.giftools.gif;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A single colour table shared by every frame of one animation, built with median cut.
 *
 * <p>Frames arrive as full colour images because scaling and the colour adjustments blend pixels, so they no longer fit
 * the 256 colours a GIF allows. Quantising the whole animation at once rather than frame by frame keeps colours from
 * drifting between frames and lets every frame reuse one table.
 */
final class Palette {

    private static final int HIST_BITS = 5;
    private static final int HIST_SIZE = 1 << (HIST_BITS * 3);
    private static final int LUT_BITS = 6;
    private static final int LUT_SIZE = 1 << (LUT_BITS * 3);

    /** GIF has no partial transparency, so a pixel is either fully clear or drawn fully opaque. */
    private static final int ALPHA_THRESHOLD = 128;

    private final int[] colours;
    private final int firstColourIndex;
    private final int transparentIndex;
    private final IndexColorModel colorModel;
    private final int[] lookup = new int[LUT_SIZE];

    private Palette(int[] colours, int transparentIndex) {
        this.colours = colours;
        this.transparentIndex = transparentIndex;
        this.firstColourIndex = transparentIndex >= 0 ? 1 : 0;
        this.colorModel = buildColorModel(colours, transparentIndex);
        Arrays.fill(lookup, -1);
    }

    int transparentIndex() {
        return transparentIndex;
    }

    static Palette build(List<BufferedImage> frames) {
        var count = new long[HIST_SIZE];
        var sumR = new long[HIST_SIZE];
        var sumG = new long[HIST_SIZE];
        var sumB = new long[HIST_SIZE];
        boolean transparent = false;

        for (var frame : frames) {
            int w = frame.getWidth();
            var row = new int[w];
            for (int y = 0; y < frame.getHeight(); y++) {
                frame.getRGB(0, y, w, 1, row, 0, w);
                for (int argb : row) {
                    if (((argb >>> 24) & 0xFF) < ALPHA_THRESHOLD) {
                        transparent = true;
                        continue;
                    }
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    int cell = cellOf(r, g, b);
                    count[cell]++;
                    sumR[cell] += r;
                    sumG[cell] += g;
                    sumB[cell] += b;
                }
            }
        }

        int populated = 0;
        for (long c : count) {
            if (c > 0) populated++;
        }
        if (populated == 0) {
            return new Palette(new int[] {0}, transparent ? 0 : -1);
        }

        var cells = new int[populated];
        int at = 0;
        for (int i = 0; i < HIST_SIZE; i++) {
            if (count[i] > 0) cells[at++] = i;
        }

        var boxes = medianCut(cells, count, transparent ? 255 : 256);
        var colours = new int[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            colours[i] = averageColour(boxes.get(i), cells, count, sumR, sumG, sumB);
        }
        return new Palette(colours, transparent ? 0 : -1);
    }

    private static List<Box> medianCut(int[] cells, long[] count, int room) {
        var boxes = new ArrayList<Box>();
        boxes.add(new Box(0, cells.length - 1));
        while (boxes.size() < room) {
            Box next = null;
            long bestScore = -1;
            for (var box : boxes) {
                if (!box.splittable || box.start >= box.end) continue;
                long score = box.population(cells, count) * (box.longestSide(cells) + 1L);
                if (score > bestScore) {
                    bestScore = score;
                    next = box;
                }
            }
            if (next == null) break;
            var halves = next.split(cells, count);
            if (halves == null) {
                next.splittable = false;
                continue;
            }
            boxes.remove(next);
            boxes.add(halves[0]);
            boxes.add(halves[1]);
        }
        return boxes;
    }

    private static int averageColour(Box box, int[] cells, long[] count, long[] sumR, long[] sumG, long[] sumB) {
        long pixels = 0;
        long r = 0;
        long g = 0;
        long b = 0;
        for (int i = box.start; i <= box.end; i++) {
            int cell = cells[i];
            pixels += count[cell];
            r += sumR[cell];
            g += sumG[cell];
            b += sumB[cell];
        }
        if (pixels == 0) return 0;
        return (int) (r / pixels) << 16 | (int) (g / pixels) << 8 | (int) (b / pixels);
    }

    private static IndexColorModel buildColorModel(int[] colours, int transparentIndex) {
        int offset = transparentIndex >= 0 ? 1 : 0;
        int size = colours.length + offset;
        var r = new byte[size];
        var g = new byte[size];
        var b = new byte[size];
        for (int i = 0; i < colours.length; i++) {
            r[i + offset] = (byte) (colours[i] >> 16);
            g[i + offset] = (byte) (colours[i] >> 8);
            b[i + offset] = (byte) colours[i];
        }
        return transparentIndex >= 0
                ? new IndexColorModel(8, size, r, g, b, transparentIndex)
                : new IndexColorModel(8, size, r, g, b);
    }

    /**
     * @return the image redrawn against this palette, so the GIF writer stores our table instead of picking its own
     */
    BufferedImage toIndexed(BufferedImage source) {
        int w = source.getWidth();
        int h = source.getHeight();
        var indexed = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
        var pixels = ((DataBufferByte) indexed.getRaster().getDataBuffer()).getData();
        var row = new int[w];
        for (int y = 0; y < h; y++) {
            source.getRGB(0, y, w, 1, row, 0, w);
            int base = y * w;
            for (int x = 0; x < w; x++) {
                int argb = row[x];
                boolean clear = transparentIndex >= 0 && ((argb >>> 24) & 0xFF) < ALPHA_THRESHOLD;
                pixels[base + x] = (byte) (clear ? transparentIndex : nearest(argb));
            }
        }
        return indexed;
    }

    private int nearest(int argb) {
        int key = ((argb >> 18) & 0x3F) << (LUT_BITS * 2) | ((argb >> 10) & 0x3F) << LUT_BITS | ((argb >> 2) & 0x3F);
        int cached = lookup[key];
        if (cached >= 0) return cached;

        int r = ((key >> (LUT_BITS * 2)) << (8 - LUT_BITS)) | 2;
        int g = (((key >> LUT_BITS) & 0x3F) << (8 - LUT_BITS)) | 2;
        int b = ((key & 0x3F) << (8 - LUT_BITS)) | 2;

        int best = firstColourIndex;
        long bestDistance = Long.MAX_VALUE;
        for (int i = 0; i < colours.length; i++) {
            int dr = r - ((colours[i] >> 16) & 0xFF);
            int dg = g - ((colours[i] >> 8) & 0xFF);
            int db = b - (colours[i] & 0xFF);
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i + firstColourIndex;
            }
        }
        lookup[key] = best;
        return best;
    }

    private static int cellOf(int r, int g, int b) {
        return (r >> (8 - HIST_BITS)) << (HIST_BITS * 2)
                | (g >> (8 - HIST_BITS)) << HIST_BITS
                | (b >> (8 - HIST_BITS));
    }

    private static final class Box {
        private final int start;
        private final int end;
        private boolean splittable = true;

        private Box(int start, int end) {
            this.start = start;
            this.end = end;
        }

        private long population(int[] cells, long[] count) {
            long total = 0;
            for (int i = start; i <= end; i++) {
                total += count[cells[i]];
            }
            return total;
        }

        private int longestSide(int[] cells) {
            return sideOf(cells, widestChannel(cells));
        }

        private int widestChannel(int[] cells) {
            int widest = 0;
            int longest = -1;
            for (int channel = 0; channel < 3; channel++) {
                int side = sideOf(cells, channel);
                if (side > longest) {
                    longest = side;
                    widest = channel;
                }
            }
            return widest;
        }

        private int sideOf(int[] cells, int channel) {
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            for (int i = start; i <= end; i++) {
                int value = coordinate(cells[i], channel);
                min = Math.min(min, value);
                max = Math.max(max, value);
            }
            return max - min;
        }

        /**
         * Sorts this box along its widest channel and cuts where half the pixels lie, so both halves carry a similar
         * share of the image rather than a similar share of the colour space.
         *
         * @return the two halves, or null if every cell would land on one side
         */
        private Box[] split(int[] cells, long[] count) {
            int channel = widestChannel(cells);
            int length = end - start + 1;
            var packed = new int[length];
            for (int i = 0; i < length; i++) {
                packed[i] = coordinate(cells[start + i], channel) << 15 | cells[start + i];
            }
            Arrays.sort(packed);
            for (int i = 0; i < length; i++) {
                cells[start + i] = packed[i] & 0x7FFF;
            }

            long total = population(cells, count);
            long running = 0;
            int cut = start;
            while (cut < end) {
                running += count[cells[cut]];
                if (running * 2 >= total) break;
                cut++;
            }
            if (cut >= end) cut = end - 1;
            if (cut < start) return null;
            return new Box[] {new Box(start, cut), new Box(cut + 1, end)};
        }

        private static int coordinate(int cell, int channel) {
            return switch (channel) {
                case 0 -> cell >> (HIST_BITS * 2);
                case 1 -> (cell >> HIST_BITS) & 0x1F;
                default -> cell & 0x1F;
            };
        }
    }
}
