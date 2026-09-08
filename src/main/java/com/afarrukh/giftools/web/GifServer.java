package com.afarrukh.giftools.web;

import com.afarrukh.giftools.gif.GifOptions;
import com.afarrukh.giftools.gif.GifProcessor;
import com.fasterxml.jackson.core.JacksonException;
import io.javalin.Javalin;
import io.javalin.config.SizeUnit;
import io.javalin.http.BadRequestResponse;
import io.javalin.http.Context;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.staticfiles.Location;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The HTTP front end: serves the editor page and renders GIFs for it. */
public final class GifServer {

    private static final Logger LOG = LoggerFactory.getLogger(GifServer.class);

    private static final long MAX_UPLOAD_MB = 64;

    private final GifStore store = new GifStore();

    private GifServer() {}

    public static Javalin start(String host, int port) {
        return new GifServer().create().start(host, port);
    }

    private Javalin create() {
        var app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.staticFiles.add("/web", Location.CLASSPATH);
            config.http.maxRequestSize = MAX_UPLOAD_MB * SizeUnit.MB.getMultiplier();
            config.jetty.multipartConfig.maxFileSize(MAX_UPLOAD_MB, SizeUnit.MB);
            config.jetty.multipartConfig.maxTotalRequestSize(MAX_UPLOAD_MB, SizeUnit.MB);
        });

        app.post("/api/gifs", this::upload);
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
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Request failed", e);
            ctx.status(500).json(Map.of("error", "Something went wrong rendering that GIF"));
        });
        return app;
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
        if (!looksLikeGif(data)) {
            throw new BadRequestResponse("That file is not a GIF");
        }
        var entry = store.put(uploaded.filename(), data, GifProcessor.inspect(data));
        ctx.json(describe(entry));
    }

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

    private static Map<String, Object> describe(GifStore.Entry entry) {
        var meta = entry.meta();
        return Map.of(
                "id", entry.id(),
                "name", entry.name(),
                "width", meta.width(),
                "height", meta.height(),
                "frameCount", meta.frameCount(),
                "durationMs", meta.durationMs(),
                "sizeBytes", meta.sizeBytes());
    }

    private static boolean looksLikeGif(byte[] data) {
        return data.length > 6 && data[0] == 'G' && data[1] == 'I' && data[2] == 'F';
    }
}
