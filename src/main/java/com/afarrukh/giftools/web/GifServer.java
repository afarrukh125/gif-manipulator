package com.afarrukh.giftools.web;

import com.afarrukh.giftools.gif.GifOptions;
import com.afarrukh.giftools.gif.GifProcessor;
import com.afarrukh.giftools.video.VideoToGif;
import com.fasterxml.jackson.core.JacksonException;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The HTTP front end: serves the editor page and renders GIFs for it. */
public final class GifServer {

    private static final Logger LOG = LoggerFactory.getLogger(GifServer.class);

    private static final long MAX_UPLOAD_MB = 64;

    private final GifStore store = new GifStore();
    private final RemoteMedia remote;

    private GifServer(String host) {
        remote = new RemoteMedia(MAX_UPLOAD_MB * SizeUnit.MB.getMultiplier(), isLoopback(host));
    }

    public static Javalin start(String host, int port) {
        return new GifServer(host).create().start(host, port);
    }

    private Javalin create() {
        var app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add("/web", Location.CLASSPATH);
            config.http.maxRequestSize = MAX_UPLOAD_MB * SizeUnit.MB.getMultiplier();
            config.jetty.multipartConfig.maxFileSize(MAX_UPLOAD_MB, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(MAX_UPLOAD_MB, SizeUnit.MB);
        });

        app.get("/api/capabilities", this::capabilities);
        app.post("/api/gifs", this::upload);
        app.post("/api/gifs/url", this::fromUrl);
        app.get("/api/gifs/{id}", this::original);
        app.post("/api/gifs/{id}/render", this::render);

        app.exception(
                IllegalArgumentException.class, (e, ctx) -> ctx.status(400).json(Map.of("error", e.getMessage())));
        // Jackson's parse failures are IOExceptions too, so they are caught first to avoid being reported as a
        // corrupt GIF when the request body is really what is malformed.
        app.exception(
                JacksonException.class,
                (e, ctx) -> ctx.status(400).json(Map.of("error", "Those options were not valid JSON")));
        app.exception(IOException.class, (e, ctx) -> {
            LOG.warn("Could not read that GIF", e);
            ctx.status(400).json(Map.of("error", "That file could not be read as a GIF: " + e.getMessage()));
        });
        app.exception(InterruptedException.class, (e, ctx) -> {
            Thread.currentThread().interrupt();
            ctx.status(503).json(Map.of("error", "That download was interrupted"));
        });
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Request failed", e);
            ctx.status(500).json(Map.of("error", "Something went wrong rendering that GIF"));
        });
        return app;
    }

    /** What this machine can open, which depends on whether it has an ffmpeg for the formats Java cannot decode. */
    private void capabilities(Context ctx) {
        ctx.json(Map.of("ffmpeg", VideoToGif.hasFullCodecSupport()));
    }

    private void upload(Context ctx) throws IOException {
        var uploaded = ctx.uploadedFile("file");
        if (uploaded == null) {
            throw new BadRequestResponse("Send the GIF as a multipart field named 'file'");
        }
        byte[] data;
        try (var content = uploaded.content()) {
            data = content.readAllBytes();
        }
        ctx.json(ingest(uploaded.filename(), data, null));
    }

    /** Opens whatever a pasted link points at, so a GIF or clip on the web can be edited without downloading it. */
    private void fromUrl(Context ctx) throws IOException, InterruptedException {
        var url = ctx.bodyAsClass(UrlRequest.class).url();
        if (url == null || url.isBlank()) {
            throw new BadRequestResponse("Send the address as a JSON body of the form {\"url\": \"...\"}");
        }
        LOG.info("Fetching {}", url);
        var download = remote.fetch(url);
        ctx.json(ingest(download.name(), download.data(), download.contentType()));
    }

    /**
     * Stores one source for editing. A clip is turned into a GIF on the way in, so that nothing past this point has to
     * know the difference.
     */
    private Map<String, Object> ingest(String name, byte[] data, String contentType) throws IOException {
        boolean converted = false;
        if (!looksLikeGif(data)) {
            if (!VideoToGif.isVideo(data)) {
                throw new IllegalArgumentException(describeUnusable(data, contentType));
            }
            data = VideoToGif.convert(data);
            name = withGifSuffix(name);
            converted = true;
        }
        return describe(store.put(name, data, GifProcessor.inspect(data)), converted);
    }

    private static String describeUnusable(byte[] data, String contentType) {
        if (contentType != null && contentType.startsWith("text/html")) {
            return "That link gave back a web page rather than a file."
                    + " Open the GIF or video itself and copy the address of the image";
        }
        if (looksLikeStillImage(data)) {
            return "That is a still image, not a GIF or a video";
        }
        return "That is not a GIF or a video";
    }

    private static String withGifSuffix(String name) {
        if (name == null || name.isBlank()) {
            return "clip.gif";
        }
        int dot = name.lastIndexOf('.');
        return (dot > 0 ? name.substring(0, dot) : name) + ".gif";
    }

    /** The JSON body {@code /api/gifs/url} takes. */
    record UrlRequest(String url) {}

    private void original(Context ctx) {
        var entry = require(ctx);
        ctx.contentType("image/gif").result(entry.data());
    }

    private void render(Context ctx) throws IOException {
        var entry = require(ctx);
        var options = ctx.bodyAsClass(GifOptions.class);
        var result = GifProcessor.process(entry.data(), options);
        ctx.contentType("image/gif")
                .header("X-Gif-Width", Integer.toString(result.width()))
                .header("X-Gif-Height", Integer.toString(result.height()))
                .header("X-Gif-Frames", Integer.toString(result.frameCount()))
                .header("X-Gif-Duration-Ms", Integer.toString(result.durationMs()))
                .header("X-Gif-Bytes", Integer.toString(result.data().length))
                .result(result.data());
    }

    private GifStore.Entry require(Context ctx) {
        var entry = store.get(ctx.pathParam("id"));
        if (entry == null) {
            throw new NotFoundResponse("That upload has expired, please drop the GIF in again");
        }
        return entry;
    }

    private static Map<String, Object> describe(GifStore.Entry entry, boolean converted) {
        var meta = entry.meta();
        var described = new HashMap<String, Object>();
        described.put("id", entry.id());
        described.put("name", entry.name());
        described.put("width", meta.width());
        described.put("height", meta.height());
        described.put("frameCount", meta.frameCount());
        described.put("durationMs", meta.durationMs());
        described.put("sizeBytes", meta.sizeBytes());
        described.put("converted", converted);
        return described;
    }

    private static boolean isLoopback(String host) {
        try {
            return InetAddress.getByName(host).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return false;
        }
    }

    private static boolean looksLikeGif(byte[] data) {
        return data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }

    private static boolean looksLikeStillImage(byte[] data) {
        if (data.length < 4) {
            return false;
        }
        boolean png = (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G';
        boolean jpeg = (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
        return png || jpeg;
    }
}
