import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

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

    public static <E> void quickSort(List<E> ls, Comparator<E> comp) {
        if(ls.size() <= 1)
            return;

        E pivot = ls.get(0);

        List<E> smaller = new ArrayList<>();
        List<E> larger = new ArrayList<>();

        for(int i = ls.size() - 1; i>0; i--) {
            E elem = ls.get(i);
            try {
                if (comp.compare(elem, pivot) < 0)
                    smaller.add(elem);
                else
                    larger.add(elem);
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

    public static void main(String[] args) {
    }

}
