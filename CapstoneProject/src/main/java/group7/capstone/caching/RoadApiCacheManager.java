package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple in-memory cache for GoogleMapsAPIController.getStreet(lat, lon, heading).
 */
public class RoadApiCacheManager {

    private static final Logger LOGGER = Logger.getLogger(RoadApiCacheManager.class.getName());

    private final GoogleMapsAPIController api;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    // config
    private int maxCacheSize = 200;
    private long maxAgeMs = 5 * 60 * 1000; // 5 minutes

    // stats
    private long hits = 0;
    private long misses = 0;
    private long apiFetches = 0;

    public RoadApiCacheManager(GoogleMapsAPIController api) {
        this.api = api;
        LOGGER.info("RoadApiCacheManager created (maxCacheSize=" + maxCacheSize + ", maxAgeMs=" + maxAgeMs + ")");
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
        LOGGER.info("RoadApiCacheManager maxCacheSize set to " + maxCacheSize);
    }

    public void setMaxAgeMs(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
        LOGGER.info("RoadApiCacheManager maxAgeMs set to " + maxAgeMs);
    }

    /**
     * Returns cached APIResponseDomain if available, otherwise fetches from API and caches it.
     */
    public APIResponseDomain getStreet(double lat, double lon, int headingDeg) {
        String key = key(lat, lon, headingDeg);

        Entry e = cache.get(key);
        if (e != null) {
            if (e.isValid(maxAgeMs)) {
                hits++;
                LOGGER.fine("CACHE HIT key=" + key + " (hits=" + hits + ", misses=" + misses + ")");
                return e.response;
            } else {
                // stale entry
                LOGGER.fine("CACHE STALE key=" + key + " ageMs=" + (System.currentTimeMillis() - e.timestamp));
            }
        } else {
            LOGGER.fine("CACHE MISS key=" + key);
        }

        misses++;
        cache.remove(key); // remove stale or ensure clean

        if (cache.size() >= maxCacheSize) {
            LOGGER.fine("Cache at capacity (size=" + cache.size() + ", max=" + maxCacheSize + "), evicting...");
            evictOldest();
        }

        long start = System.currentTimeMillis();
        APIResponseDomain resp;
        try {
            resp = api.getStreet(lat, lon, headingDeg);
            apiFetches++;
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "API getStreet FAILED key=" + key + " lat=" + lat + " lon=" + lon + " heading=" + headingDeg, ex);
            throw ex;
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            LOGGER.fine("API getStreet completed key=" + key + " in " + elapsed + "ms");
        }

        cache.put(key, new Entry(resp));
        LOGGER.fine("Cached key=" + key + " (size=" + cache.size() + ")");
        return resp;
    }

    private String key(double lat, double lon, int headingDeg) {
        double rLat = Math.round(lat * 10000.0) / 10000.0;
        double rLon = Math.round(lon * 10000.0) / 10000.0;

        // keep heading stable (0-359)
        int h = headingDeg % 360;
        if (h < 0) h += 360;

        return String.format("%.4f_%.4f_%03d", rLat, rLon, h);
    }

    private void evictOldest() {
        if (cache.isEmpty()) return;

        List<Map.Entry<String, Entry>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Comparator.comparingLong(a -> a.getValue().timestamp));

        int toRemove = Math.max(1, cache.size() - maxCacheSize + 1);
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            String removedKey = entries.get(i).getKey();
            cache.remove(removedKey);
            LOGGER.finer("Evicted key=" + removedKey);
        }

        LOGGER.fine("Evicted " + toRemove + " old cache entries (size=" + cache.size() + ")");
    }

    public String getStats() {
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total;

        String stats = String.format(
                "RoadCache - size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, apiFetches=%d",
                cache.size(), maxCacheSize, hits, misses, hitRate * 100.0, apiFetches
        );

        LOGGER.fine("Stats requested: " + stats);
        return stats;
    }

    private static class Entry {
        final APIResponseDomain response;
        final long timestamp;

        Entry(APIResponseDomain response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isValid(long maxAgeMs) {
            return (System.currentTimeMillis() - timestamp) < maxAgeMs;
        }
    }
}
