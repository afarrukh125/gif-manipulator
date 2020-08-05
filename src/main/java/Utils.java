import java.awt.*;
import java.awt.image.BufferedImage;

public class Utils {

    /**
     * Credit: https://stackoverflow.com/a/13605411
     * @param img The image to convert to a {@link BufferedImage}
     * @return A {@link BufferedImage} equivalent of the image
     */
    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

}
