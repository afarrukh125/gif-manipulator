package com.afarrukh.giftools;

import static java.lang.Runtime.getRuntime;
import static java.util.concurrent.Executors.newFixedThreadPool;

import at.dhyan.open_imaging.GifDecoder;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "create")
public class CreateCommand implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(CreateCommand.class);

    @Option(name = "--file-path")
    private String filePath;

    public void run() {
        if (filePath == null) {
            Utils.setupUILookAndFeel();
            var chooser = setupFileChooser();
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                filePath = chooser.getSelectedFile().getAbsolutePath();
            }
        }

        System.out.println(filePath);
        if (filePath == null) {
            throw new IllegalArgumentException("No path provided, exiting...");
        }
        final FileInputStream data;
        try {
            data = new FileInputStream(filePath);
            var finalLocation = "target/" + new File(filePath).getName().replace(".gif", "");
            writeImages(data, finalLocation);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
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

    private static void writeImages(FileInputStream data, String outputFolder)
            throws IOException, InterruptedException {
        var gif = GifDecoder.read(data);
        int frameCount = gif.getFrameCount();
        try (var executorService = newFixedThreadPool(getRuntime().availableProcessors() * 2)) {
            for (int i = 0; i < frameCount; i++) {
                var img = gif.getFrame(i);
                int index = i;
                executorService.execute(() -> outputFrame(outputFolder, index, img));
            }
            executorService.shutdown();
            executorService.awaitTermination(5, TimeUnit.MINUTES);
        }
        openFolder(outputFolder);
    }

    private static void openFolder(String outputFolder) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            try {
                desktop.open(new File(outputFolder));
            } catch (IOException e) {
                LOG.info("Could not open folder");
            }
        } else {
            LOG.info("Converted GIF to images successfully! Check the files in directory {}", outputFolder);
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
}
