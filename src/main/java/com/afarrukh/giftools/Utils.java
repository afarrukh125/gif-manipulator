package com.afarrukh.giftools;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Utils {

    public static final String DELAYS_FILE_NAME = "delays.txt";

    /**
     * Credit: https://stackoverflow.com/a/13605411
     * @param img The image to convert to a {@link BufferedImage}
     * @return A {@link BufferedImage} equivalent of the image
     */
    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // BufferedImage.TYPE_INT_ARGB for transparency
        var image = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        var graphics = image.createGraphics();
        graphics.drawImage(img, 0, 0, null);
        graphics.dispose();

        // Return the buffered image
        return image;
    }

    /**
     * Records the delay of every extracted frame so the original playback speed can be restored later.
     * @param folder The folder the frames were written to
     * @param delays The delay of each frame, in hundredths of a second, indexed by frame number
     */
    public static void writeDelays(File folder, List<Integer> delays) throws IOException {
        var lines = new ArrayList<String>(delays.size());
        for (int i = 0; i < delays.size(); i++) {
            lines.add(i + " " + delays.get(i));
        }
        Files.write(folder.toPath().resolve(DELAYS_FILE_NAME), lines);
    }

    /**
     * @param folder The folder of frames to look for recorded delays in
     * @return Frame number to delay in hundredths of a second, empty if the folder has no recorded delays
     */
    public static Map<Integer, Integer> readDelays(File folder) {
        var path = folder.toPath().resolve(DELAYS_FILE_NAME);
        var delays = new HashMap<Integer, Integer>();
        if (!Files.isRegularFile(path)) {
            return delays;
        }
        try {
            for (var line : Files.readAllLines(path)) {
                var parts = line.trim().split("\\s+");
                if (parts.length == 2) {
                    delays.put(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return delays;
    }

    public static <E> void quickSort(List<E> ls, Comparator<E> comp) {
        if (ls.size() <= 1) {
            return;
        }

        E pivot = ls.getFirst();

        List<E> smaller = new ArrayList<>();
        List<E> larger = new ArrayList<>();

        for (int i = ls.size() - 1; i > 0; i--) {
            E elem = ls.get(i);
            try {
                if (comp.compare(elem, pivot) < 0) smaller.add(elem);
                else larger.add(elem);
            } catch (NumberFormatException ignored) {
                ls.remove(i);
            }
        }

        quickSort(smaller, comp);
        quickSort(larger, comp);

        ls.clear();

        ls.addAll(smaller);
        ls.add(pivot);
        ls.addAll(larger);
    }

    public static void main(String[] args) {}
}
