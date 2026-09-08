package com.afarrukh.giftools;

import com.afarrukh.giftools.web.GifServer;
import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(name = "serve", description = "Runs the browser based GIF editor")
public class ServeCommand implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ServeCommand.class);

    @Option(name = "--port", description = "Port to listen on, 7000 by default")
    private int port = 7000;

    @Option(name = "--no-open", description = "Do not open the editor in a browser on startup")
    private boolean noOpen;

    public void run() {
        var app = GifServer.start(port);
        var url = "http://localhost:" + app.port();
        LOG.info("GIF editor running at {}", url);
        if (!noOpen) {
            openBrowser(url);
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
