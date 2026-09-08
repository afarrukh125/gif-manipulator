package com.afarrukh.giftools.web;

import com.afarrukh.giftools.gif.GifProcessor;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds uploaded GIFs in memory so the editor can re-render one repeatedly without uploading it again.
 *
 * <p>Nothing here is written to disk, and an upload is dropped once it goes stale or once newer uploads need the room.
 */
final class GifStore {

    private static final Duration TTL = Duration.ofHours(2);
    private static final long MAX_TOTAL_BYTES = 512L * 1024 * 1024;

    record Entry(String id, String name, byte[] data, GifProcessor.Meta meta, long uploadedAt) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private long totalBytes;

    synchronized Entry put(String name, byte[] data, GifProcessor.Meta meta) {
        var entry = new Entry(UUID.randomUUID().toString(), name, data, meta, System.currentTimeMillis());
        entries.put(entry.id(), entry);
        totalBytes += data.length;
        evict();
        return entry;
    }

    synchronized Entry get(String id) {
        var entry = entries.get(id);
        if (entry == null) {
            return null;
        }
        if (isStale(entry)) {
            remove(id);
            return null;
        }
        return entry;
    }

    private void evict() {
        for (var id : new ArrayList<>(entries.keySet())) {
            if (isStale(entries.get(id))) {
                remove(id);
            }
        }
        var oldestFirst = entries.keySet().iterator();
        while (totalBytes > MAX_TOTAL_BYTES && oldestFirst.hasNext()) {
            var entry = entries.get(oldestFirst.next());
            oldestFirst.remove();
            totalBytes -= entry.data().length;
        }
    }

    private void remove(String id) {
        var removed = entries.remove(id);
        if (removed != null) {
            totalBytes -= removed.data().length;
        }
    }

    private static boolean isStale(Entry entry) {
        return System.currentTimeMillis() - entry.uploadedAt() > TTL.toMillis();
    }
}
