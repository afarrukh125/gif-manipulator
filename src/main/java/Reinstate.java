import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Reinstate {

    public static void main(String[] args) throws IOException {
        String newLocation = new File(args[0]).getName().replace(".gif", "");
        reinstate(newLocation, 49);
    }

    private static void reinstate(String outPath, int startIdx) throws IOException {
        File directory = new File(outPath);
        System.out.println(directory.isDirectory());

        if (directory.isDirectory()) {
            File[] fileArr = Objects.requireNonNull(directory.listFiles(), "Need a directory");

            List<File> files = Arrays.stream(fileArr)
                    .sorted(Comparator.comparingInt(o -> Integer.parseInt(o.getName().replace(".png", ""))))
                    .collect(Collectors.toList());

            int numFiles = files.size();

            ImageOutputStream output =
                    new FileImageOutputStream(new File(outPath + "/" + outPath + ".gif"));

            BufferedImage firstImage = ImageIO.read(files.get(startIdx));

            GifSequenceWriter writer =
                    new GifSequenceWriter(output, firstImage.getType(), 1, true);

            writer.writeToSequence(firstImage);

            for (int i = startIdx + 1; i < numFiles; i++) {
                File file = files.get(i);
                writer.writeToSequence(ImageIO.read(file));
                System.out.println(file.getName());
            }

            for (int i = 0; i < startIdx; i++) {
                File file = files.get(i);
                writer.writeToSequence(ImageIO.read(file));
                System.out.println(file.getName());
            }

            writer.close();
            output.close();
        }
    }
}
