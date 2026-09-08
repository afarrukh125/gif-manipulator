package com.afarrukh.giftools.gif;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/** Writes a list of full canvas frames back out as an animated GIF. */
public final class GifEncoder {

    private GifEncoder() {}

    /**
     * @param frames one complete, already composited image per frame
     * @param delays how long each frame is shown, in hundredths of a second
     * @param loop whether the animation repeats forever rather than playing once
     */
    public static byte[] encode(List<BufferedImage> frames, List<Integer> delays, boolean loop) throws IOException {
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("Nothing to encode, the frame selection is empty");
        }
        var palette = Palette.build(frames);
        var writers = ImageIO.getImageWritersBySuffix("gif");
        if (!writers.hasNext()) {
            throw new IOException("This JVM has no GIF image writer");
        }
        var writer = writers.next();
        var params = writer.getDefaultWriteParam();
        var bytes = new ByteArrayOutputStream();

        try (var output = new MemoryCacheImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            for (int i = 0; i < frames.size(); i++) {
                var indexed = palette.toIndexed(frames.get(i));
                var type = ImageTypeSpecifier.createFromRenderedImage(indexed);
                var metadata = writer.getDefaultImageMetadata(type, params);
                describe(metadata, delays.get(i), palette.transparentIndex(), loop && i == 0);
                writer.writeToSequence(new IIOImage(indexed, null, metadata), params);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static void describe(IIOMetadata metadata, int delay, int transparentIndex, boolean addLoop)
            throws IOException {
        var format = metadata.getNativeMetadataFormatName();
        var root = (IIOMetadataNode) metadata.getAsTree(format);
        boolean transparent = transparentIndex >= 0;

        var control = child(root, "GraphicControlExtension");
        // Every frame here is a complete canvas, so an opaque animation can leave the previous frame in place. A
        // transparent one must not: without a clear, holes in a frame would still show the frame before it.
        control.setAttribute("disposalMethod", transparent ? "restoreToBackgroundColor" : "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", transparent ? "TRUE" : "FALSE");
        control.setAttribute("transparentColorIndex", Integer.toString(Math.max(transparentIndex, 0)));
        control.setAttribute("delayTime", Integer.toString(delay));

        if (addLoop) {
            var netscape = new IIOMetadataNode("ApplicationExtension");
            netscape.setAttribute("applicationID", "NETSCAPE");
            netscape.setAttribute("authenticationCode", "2.0");
            netscape.setUserObject(new byte[] {1, 0, 0});
            child(root, "ApplicationExtensions").appendChild(netscape);
        }

        metadata.setFromTree(format, root);
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        for (int i = 0; i < parent.getLength(); i++) {
            if (parent.item(i).getNodeName().equalsIgnoreCase(name)) {
                return (IIOMetadataNode) parent.item(i);
            }
        }
        var created = new IIOMetadataNode(name);
        parent.appendChild(created);
        return created;
    }
}
