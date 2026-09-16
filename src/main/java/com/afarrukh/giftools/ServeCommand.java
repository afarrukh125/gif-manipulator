package com.afarrukh.giftools;

import com.afarrukh.giftools.web.GifServer;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "serve", description = "Runs the browser based GIF editor")
public class ServeCommand implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ServeCommand.class);

    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7000;

    @Option(name = "--port", description = "Port to listen on, $PORT or 7000 by default")
    private int port = envPort();

    @Option(
            name = "--host",
            description = "Address to bind to, $HOST or 127.0.0.1 by default."
                    + " Pass 0.0.0.0 to let other machines reach the editor")
    private String host = envOr("HOST", DEFAULT_HOST);

    @Option(name = "--no-open", description = "Do not open the editor in a browser on startup")
    private boolean noOpen;

    @Option(name = "--log-file", description = "Append the log to this file instead of writing it to the console")
    private String logFile = envOr("LOG_FILE", null);

    public void run() {
        if (logFile != null) {
            FileLogging.sendLogsTo(Path.of(logFile));
        }
        var app = GifServer.start(host, port);
        var url = "http://localhost:" + app.port();
        LOG.info("GIF editor running at {}", url);
        if (!DEFAULT_HOST.equals(host)) {
            LOG.warn(
                    "Bound to {}, so anything that can reach this machine can upload to the editor;"
                            + " it has no password of its own",
                    host);
        }
        if (!noOpen) {
            openBrowser(url);
        }
    }

    private static String envOr(String name, String fallback) {
        var value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int envPort() {
        var value = envOr("PORT", null);
        if (value == null) {
            return DEFAULT_PORT;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("PORT is not a number: " + value, e);
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(url));
            }
        } catch (IOException | UnsupportedOperationException e) {
            LOG.info("Could not open a browser automatically, visit {} yourself", url);
        }
    }
}
