package com.afarrukh.giftools;

import at.dhyan.open_imaging.GifSequenceWriter;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "reinstate")
public class ReinstateCommand implements Runnable {
    private static final Logger LOG = LoggerFactory.getLogger(ReinstateCommand.class);

    @Option(name = "--folder-path")
    private String folderPath;

    public void run() {
        CreateCommand.setupUILookAndFeel();
        JOptionPane.showMessageDialog(
                null,
                "Before using this, please ensure you have a "
                        + "folder of .png files numbered in some ordering. If you don't please use the creator to generate "
                        + ".png files from a GIF.");

        if (folderPath == null) {
            var chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                var selectedFile = chooser.getSelectedFile();
                if (!selectedFile.isDirectory()) {
                    JOptionPane.showMessageDialog(null, "Please select a folder.");
                    System.exit(0);
                } else folderPath = selectedFile.getAbsolutePath();
            }
        }
        LOG.info("Selected folder path: {}", folderPath);
        System.out.println(folderPath);
        var newLocation = new File(folderPath.replace(".gif", "")).getAbsolutePath();
        try {
            reinstate(newLocation, 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void reinstate(String outPath, int startIdx) throws IOException {
        var directory = new File(outPath);
        LOG.info("Directory: {}", directory.isDirectory());

        if (directory.isDirectory()) {
            var fileArr = Objects.requireNonNull(directory.listFiles(), "Need a directory");

            var files = Arrays.stream(fileArr).collect(Collectors.toList());

            var copyList = new ArrayList<>(files);
            Utils.quickSort(
                    copyList,
                    Comparator.comparingInt(o -> Integer.parseInt(o.getName().replace(".png", ""))));

            if (copyList.isEmpty()) {
                LOG.error("No numbered .png files found");
                System.exit(0);
            }

            if (startIdx >= copyList.size()) {
                LOG.info(
                        "Can't start looping at that index, it is higher than how many numbered files there are: {}",
                        copyList.size());
                System.exit(0);
            }

            files = copyList;
            int numFiles = files.size();
            var output = new FileImageOutputStream(new File(outPath + ".gif"));
            var firstImage = ImageIO.read(files.get(startIdx));
            var writer = new GifSequenceWriter(output, firstImage.getType(), 1, true);

            writeToSequence(firstImage, writer);

            Stream.concat(files.subList(startIdx + 1, numFiles).stream(), files.subList(0, startIdx).stream())
                    .peek(file -> LOG.info("Preparing to write file: {}", file.getName()))
                    .map(ReinstateCommand::createBufferedImageFromFile)
                    .forEach(img -> writeToSequence(img, writer));

            writer.close();
            output.close();
        }
    }

    private static void writeToSequence(BufferedImage img, GifSequenceWriter writer) {
        try {
            writer.writeToSequence(img);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage createBufferedImageFromFile(File file) {
        try {
            return ImageIO.read(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static BufferedImage flip(BufferedImage image) {
        var tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-image.getWidth(null), 0);
        var op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(image, null);
    }
}
