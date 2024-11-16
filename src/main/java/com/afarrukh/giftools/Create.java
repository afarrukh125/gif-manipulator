package com.afarrukh.giftools;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;

import at.dhyan.open_imaging.GifDecoder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

public class Create {

    /**
     * Writes a folder of images to the directory this program was ran in, in a folder with the same name as the image
     * @param args The path to the gif file that should be converted to images
     * @throws IOException If file not found or similar
     */
    public static void main(String[] args) throws IOException {
        String path = null;
        if (args.length == 0) {
            setupUILookAndFeel();
            var chooser = setupFileChooser();
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                path = chooser.getSelectedFile().getAbsolutePath();
            }
        } else {
            path = args[0];
        }

        System.out.println(path);
        if (path == null) {
            throw new IllegalArgumentException("No path provided, exiting...");
        }
        final var data = new FileInputStream(path);
        var finalLocation = "target/" + new File(path).getName().replace(".gif", "");
        writeImages(data, finalLocation);

        var outputAbsolutePath = new File(finalLocation).getAbsolutePath();
        JOptionPane.showMessageDialog(
                null,
                "Converted " + new File(path).getName() + " to images successfully! Check the files in directory "
                        + outputAbsolutePath + ".");
    }

    private static JFileChooser setupFileChooser() {
        var chooser = new JFileChooser();
        var initialFolder = new File(System.getProperty("user.home"));
        chooser.setCurrentDirectory(initialFolder);
        chooser.setAcceptAllFileFilterUsed(false);
        var fileFilter = new FileNameExtensionFilter("GIF files", "gif");
        chooser.addChoosableFileFilter(fileFilter);
        return chooser;
    }

    private static void setupUILookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (ClassNotFoundException
                | UnsupportedLookAndFeelException
                | IllegalAccessException
                | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Writes the actual images
     * @param data The input stream to use to write
     * @param outFileName The name of the folder, by default, the name of the file
     * @throws IOException If file not found or similar
     */
    private static void writeImages(FileInputStream data, String outFileName) throws IOException {
        writeParallel(data, outFileName);
    }

    private static void writeParallel(FileInputStream data, String outFileName) throws IOException {
         var gif = GifDecoder.read(data);
         int frameCount = gif.getFrameCount();
        try (var executorService = newFixedThreadPool(getRuntime().availableProcessors() * 2)) {
            for (int i = 0; i < frameCount; i++) {
                var img = gif.getFrame(i);
                int finalI = i;
                executorService.execute(() -> outputFrame(outFileName, finalI, img));
            }
            executorService.shutdown();
            try {
                executorService.awaitTermination(5, TimeUnit.MINUTES);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void outputFrame(String outFileName, int index, BufferedImage img) {
        var file = new File(outFileName + "/" + index + ".png");
        if (file.mkdirs()) {
            try {
                ImageIO.write(img, "png", file);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void writeSingleThreaded(FileInputStream data, String outFileName) throws IOException {
        var gif = GifDecoder.read(data);
        int frameCount = gif.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            var img = gif.getFrame(i);
            outputFrame(outFileName, i, img);
        }
    }

    /**
     * Writes the actual images
     * @param data The input stream to use to write
     * @param outFileName The name of the folder, by default, the name of the file
     * @param xResolution The x resolution to scale to
     * @param yResolution The y resolution to scale to
     * @throws IOException If file not found or similar
     */
    private static void writeImages(FileInputStream data, String outFileName, int xResolution, int yResolution)
            throws IOException {
        var gif = GifDecoder.read(data);
        int frameCount = gif.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            var img = gif.getFrame(i);
            img = Utils.toBufferedImage(img.getScaledInstance(xResolution, yResolution, Image.SCALE_DEFAULT));
            var file = new File(outFileName + "/" + i + ".png");
            if (file.mkdirs()) ImageIO.write(img, "png", file);
        }
    }
}
