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
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
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

    private static final Pattern FRAME_FILE = Pattern.compile("\\d+\\.png", Pattern.CASE_INSENSITIVE);

    private static final int DEFAULT_DELAY_HUNDREDTHS = 10;

    /**
     * Renderers do not play a delay this short literally, they substitute a tenth of a second, so a frame delay
     * below this would silently make the gif slower than the one the frames came from.
     */
    private static final int MIN_DELAY_HUNDREDTHS = 2;

    @Option(name = "--folder-path")
    private String folderPath;

    @Option(name = "--start-index")
    private int startIndex = 0;

    @Option(name = "--frame-delay-ms")
    private int frameDelayMs = 0;

    public void run() {
        CreateCommand.setupUILookAndFeel();

        if (folderPath == null) {
            JOptionPane.showMessageDialog(
                    null,
                    "Before using this, please ensure you have a "
                            + "folder of .png files numbered in some ordering. If you don't please use the creator to generate "
                            + ".png files from a GIF.");
            var chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File("target/"));
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
            reinstate(newLocation, startIndex, toHundredths(frameDelayMs));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void reinstate(String outPath, int startIdx, int delayOverride) throws IOException {
        var directory = new File(outPath);
        LOG.info("Directory: {}", directory.isDirectory());

        if (directory.isDirectory()) {
            var fileArr = Objects.requireNonNull(directory.listFiles(), "Need a directory");

            var files = Arrays.stream(fileArr)
                    .filter(file -> FRAME_FILE.matcher(file.getName()).matches())
                    .collect(Collectors.toList());

            var copyList = new ArrayList<>(files);
            Utils.quickSort(copyList, Comparator.comparingInt(ReinstateCommand::frameIndex));

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

            var delays = delayOverride > 0 ? Map.<Integer, Integer>of() : Utils.readDelays(directory);
            if (delays.isEmpty() && delayOverride <= 0) {
                LOG.warn(
                        "No {} in this folder, falling back to {} hundredths of a second per frame",
                        Utils.DELAYS_FILE_NAME,
                        DEFAULT_DELAY_HUNDREDTHS);
            }

            var ordered = Stream.concat(
                            files.subList(startIdx, numFiles).stream(), files.subList(0, startIdx).stream())
                    .toList();

            var output = new FileImageOutputStream(new File(outPath + ".gif"));
            var firstImage = ImageIO.read(ordered.getFirst());
            var writer = new GifSequenceWriter(output, firstImage.getType(), DEFAULT_DELAY_HUNDREDTHS * 10, true);

            writeToSequence(firstImage, delayFor(ordered.getFirst(), delays, delayOverride), writer);

            ordered.stream().skip(1).forEach(file -> {
                LOG.info("Preparing to write file: {}", file.getName());
                writeToSequence(createBufferedImageFromFile(file), delayFor(file, delays, delayOverride), writer);
            });

            writer.close();
            output.close();
        }
    }

    private static int frameIndex(File file) {
        return Integer.parseInt(file.getName().replace(".png", ""));
    }

    private static int delayFor(File file, Map<Integer, Integer> delays, int delayOverride) {
        if (delayOverride > 0) {
            return delayOverride;
        }
        int delay = delays.getOrDefault(frameIndex(file), DEFAULT_DELAY_HUNDREDTHS);
        return delay < MIN_DELAY_HUNDREDTHS ? DEFAULT_DELAY_HUNDREDTHS : delay;
    }

    private static int toHundredths(int milliseconds) {
        return milliseconds <= 0 ? 0 : Math.max(MIN_DELAY_HUNDREDTHS, Math.round(milliseconds / 10f));
    }

    private static void writeToSequence(BufferedImage img, int delayHundredths, GifSequenceWriter writer) {
        try {
            writer.writeToSequence(img, delayHundredths);
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
