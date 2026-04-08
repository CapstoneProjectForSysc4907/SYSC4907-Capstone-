package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory cache for GoogleMapsAPIController.getStreet(lat, lon, heading).
 */
public class RoadApiCacheManager {

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
    }

    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }

    public void setMaxAgeMs(long maxAgeMs) {
        this.maxAgeMs = maxAgeMs;
    }

    /**
     * Returns cached APIResponseDomain if available, if not it fetches from API and caches what it gets.
     */
    public APIResponseDomain getStreet(double lat, double lon, int headingDeg) {
        String key = key(lat, lon, headingDeg);
        Entry e = cache.get(key);
        if (e != null) {
            if (e.isValid(maxAgeMs)) {
                hits++;
                return e.response;
            }
            cache.remove(key);
        }
        misses++;
        if (cache.size() >= maxCacheSize) {
            evictOldest();
        }
        APIResponseDomain resp;
        try {
            resp = api.getStreet(lat, lon, headingDeg);
            apiFetches++;
        } catch (Exception ex) {
            System.out.println("API call failed for key=" + key + ": " + ex.getMessage());
            throw ex;
        }

        cache.put(key, new Entry(resp));
        return resp;
    }

    private String key(double lat, double lon, int headingDeg) {
        double rLat = Math.round(lat * 10000.0) / 10000.0;
        double rLon = Math.round(lon * 10000.0) / 10000.0;

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
            cache.remove(entries.get(i).getKey());
        }
    }

    public String getStats() {
        long total = hits + misses;
        double hitRate = total == 0 ? 0.0 : (double) hits / total;
        return String.format(
                "RoadCache - size=%d/%d, hits=%d, misses=%d, hitRate=%.1f%%, apiFetches=%d",
                cache.size(), maxCacheSize, hits, misses, hitRate * 100.0, apiFetches
        );
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
