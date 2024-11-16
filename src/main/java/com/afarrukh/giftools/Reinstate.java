package com.afarrukh.giftools;

import at.dhyan.open_imaging.GifSequenceWriter;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.*;

public class Reinstate {

    public static void main(String[] args) throws IOException {
        JOptionPane.showMessageDialog(
                null,
                "Before using this, please ensure you have a "
                        + "folder of .png files numbered in some ordering. If you don't please use the creator to generate "
                        + ".png files from a GIF.");

        var path = "";

        if (args.length == 0) {
            var chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                var selectedFile = chooser.getSelectedFile();
                if (!selectedFile.isDirectory()) {
                    JOptionPane.showMessageDialog(null, "Please select a folder.");
                    System.exit(0);
                } else path = selectedFile.getAbsolutePath();
            }
        }
        System.out.println(path);
        var newLocation = new File(path.replace(".gif", "")).getAbsolutePath();
        reinstate(newLocation, 0);
    }

    private static void reinstate(String outPath, int startIdx) throws IOException {
        var directory = new File(outPath);
        System.out.println(directory.isDirectory());

        if (directory.isDirectory()) {
            var fileArr = Objects.requireNonNull(directory.listFiles(), "Need a directory");

            var files = Arrays.stream(fileArr).collect(Collectors.toList());

            var copyList = new ArrayList<>(files);
            Utils.quickSort(
                    copyList,
                    Comparator.comparingInt(o -> Integer.parseInt(o.getName().replace(".png", ""))));

            if (copyList.size() == 0) {
                System.out.println("No numbered .png files found");
                System.exit(0);
            }

            if (startIdx >= copyList.size()) {
                System.out.println(
                        "Can't start looping at that index, it is higher than how many numbered files there are: "
                                + copyList.size());
                System.exit(0);
            }

            files = copyList;

            int numFiles = files.size();

            var output = new FileImageOutputStream(new File(outPath + ".gif"));

            var firstImage = ImageIO.read(files.get(startIdx));

            var writer = new GifSequenceWriter(output, firstImage.getType(), 1, true);

            writer.writeToSequence(firstImage);

            for (int i = startIdx + 1; i < numFiles; i++) {
                var file = files.get(i);
                writer.writeToSequence(ImageIO.read(file));
                System.out.println(file.getName());
            }

            for (int i = 0; i < startIdx; i++) {
                var file = files.get(i);
                writer.writeToSequence(ImageIO.read(file));
                System.out.println(file.getName());
            }

            writer.close();
            output.close();
        }
    }

    private static BufferedImage flip(BufferedImage image) {
        var tx = AffineTransform.getScaleInstance(-1, 1);
        tx.translate(-image.getWidth(null), 0);
        var op = new AffineTransformOp(tx, AffineTransformOp.TYPE_NEAREST_NEIGHBOR);
        return op.filter(image, null);
    }
}
