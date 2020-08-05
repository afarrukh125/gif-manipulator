import at.dhyan.open_imaging.GifDecoder;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

public class Create {

    /**
     * Writes a folder of images to the directory this program was ran in, in a folder with the same name as the image
     * @param args The path to the gif file that should be converted to images
     * @throws IOException If file not found or similar
     */
    public static void main(String[] args) throws IOException {
        String path = "";
        if(args.length == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setCurrentDirectory(new File(System.getProperty("user.home")));
            int result = chooser.showOpenDialog(null);
            if(result == JFileChooser.APPROVE_OPTION) {
                File selectedFile = chooser.getSelectedFile();
                if(!selectedFile.getName().contains(".gif")) {
                    JOptionPane.showMessageDialog(null, "Please select a GIF.");
                    System.exit(0);
                } else
                    path = selectedFile.getAbsolutePath();
            }
        }
        else
            path = args[0];
        final FileInputStream data = new FileInputStream(path);
        String newLocation = new File(path).getName().replace(".gif", "");
        writeImages(data, newLocation);

        JOptionPane.showMessageDialog(null, "Converted " + new File(path).getName() +
                " to images successfully! Check the new directory.");
    }

    /**
     * Writes the actual images
     * @param data The input stream to use to write
     * @param outFileName The name of the folder, by default, the name of the file
     * @throws IOException If file not found or similar
     */
    private static void writeImages(FileInputStream data, String outFileName) throws IOException {
        final GifDecoder.GifImage gif = GifDecoder.read(data);
        final int frameCount = gif.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            BufferedImage img = gif.getFrame(i);
            File file = new File(outFileName + "/" + i + ".png");
            if (file.mkdirs())
                ImageIO.write(img, "png", file);
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
    private static void writeImages(FileInputStream data, String outFileName, int xResolution, int yResolution) throws IOException {
        final GifDecoder.GifImage gif = GifDecoder.read(data);
        final int frameCount = gif.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            BufferedImage img = gif.getFrame(i);
            img = Utils.toBufferedImage(img.getScaledInstance(xResolution, yResolution, Image.SCALE_DEFAULT));
            File file = new File(outFileName + "/" + i + ".png");
            if (file.mkdirs())
                ImageIO.write(img, "png", file);
        }
    }
}
