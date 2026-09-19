package com.afarrukh.giftools.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/** Downloads whatever a pasted link points at, so a GIF or a clip can be opened without saving it first. */
final class RemoteMedia {

    /**
     * Sent because several hosts that serve GIFs and clips - Discord's proxy and Tenor among them - answer a request
     * with no user agent with a redirect to a landing page, or with nothing at all.
     */
    private static final String USER_AGENT =
            "Mozilla/5.0 (compatible; giftools/1.0; +https://github.com/afarrukh/gif-manipulator)";

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final long maxBytes;
    private final boolean allowPrivateAddresses;

    /**
     * @param allowPrivateAddresses whether links may point inside this machine or its network. True only while the
     *     editor is bound to loopback, so that an editor opened up to a network cannot be used by whoever reaches it as
     *     a way to read addresses they could not reach themselves.
     */
    RemoteMedia(long maxBytes, boolean allowPrivateAddresses) {
        this.maxBytes = maxBytes;
        this.allowPrivateAddresses = allowPrivateAddresses;
    }

    record Download(byte[] data, String name, String contentType) {}

    Download fetch(String rawUrl) throws IOException, InterruptedException {
        var uri = parse(rawUrl);
        checkReachable(uri);

        var request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/gif,video/mp4,image/*,video/*;q=0.9,*/*;q=0.5")
                .GET()
                .build();

        HttpResponse<InputStream> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("No such host: " + uri.getHost(), e);
        } catch (IOException e) {
            var reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            throw new IllegalArgumentException("Could not reach " + uri.getHost() + ": " + reason, e);
        }
        byte[] data;
        try (var body = response.body()) {
            if (response.statusCode() >= 400) {
                throw new IllegalArgumentException("That link answered with HTTP " + response.statusCode());
            }
            // Read one byte past the limit rather than trusting Content-Length, which a server is free to understate.
            data = body.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
        }
        if (data.length > maxBytes) {
            throw new IllegalArgumentException("That file is larger than " + maxBytes / (1024 * 1024) + " MB");
        }
        if (data.length == 0) {
            throw new IllegalArgumentException("That link returned an empty file");
        }
        var contentType = response.headers().firstValue("content-type").orElse("");
        return new Download(data, nameFrom(response.uri()), contentType.toLowerCase(Locale.ROOT));
    }

    private static URI parse(String rawUrl) {
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("That does not look like a web address");
        }
        var scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Only http and https links can be opened");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("That address has no host in it");
        }
        return uri;
    }

    private void checkReachable(URI uri) {
        if (allowPrivateAddresses) {
            return;
        }
        InetAddress address;
        try {
            address = InetAddress.getByName(uri.getHost());
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException("No such host: " + uri.getHost());
        }
        if (address.isLoopbackAddress()
                || address.isAnyLocalAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()) {
            throw new IllegalArgumentException("Links into a private network are refused while the editor is bound to"
                    + " an address other than 127.0.0.1");
        }
    }

    /** The last path segment of the address the download actually came from, after any redirects. */
    private static String nameFrom(URI uri) {
        var path = uri.getPath();
        if (path == null || path.isBlank() || path.endsWith("/")) {
            return uri.getHost();
        }
        var name = path.substring(path.lastIndexOf('/') + 1);
        return name.isBlank() ? uri.getHost() : name;
    }
}
