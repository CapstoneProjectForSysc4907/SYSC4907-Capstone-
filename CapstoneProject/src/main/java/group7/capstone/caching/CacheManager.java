package group7.capstone.caching;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Cache Manager for Map Data
 */
public class CacheManager {
    private static final Logger LOGGER = Logger.getLogger(CacheManager.class.getName());

    private final Map<String, CachedMapData> cache;
    private final GoogleMapsAPIController apiController;
    
    //configuration
    private int maxCacheSize;
    private double preloadDistanceKm;
    private final long maxCacheAgeMs;
    
    //for logs
    private long cacheHits;
    private long cacheMisses;
    private long totalFetches;
    
    /**
     * @param apiController The API controller to fetch data from
     */
    public CacheManager(GoogleMapsAPIController apiController) {
        this(apiController, loadDefaultConfig());
    }
    
    /**
     * Create a CacheManager with custom configuration
     * @param apiController The API controller to fetch data from
     * @param config Configuration properties
     */
    public CacheManager(GoogleMapsAPIController apiController, Properties config) {
        this.apiController = apiController;
        this.cache = new ConcurrentHashMap<>();
        this.cacheHits = 0;
        this.cacheMisses = 0;
        this.totalFetches = 0;
        
        //load configuration
        this.maxCacheSize = Integer.parseInt(config.getProperty("max.cache.size", "100"));
        this.preloadDistanceKm = Double.parseDouble(config.getProperty("preload.distance.km", "2.0"));
        this.maxCacheAgeMs = Long.parseLong(config.getProperty("max.cache.age.ms", "3600000"));
        
        LOGGER.info(String.format("CacheManager initialized: maxSize=%d, preloadDistance=%.1fkm",
                maxCacheSize, preloadDistanceKm));
    }
    
    /**
     * Load default configuration
     */
    private static Properties loadDefaultConfig() {
        Properties props = new Properties();
        props.setProperty("max.cache.size", "100");
        props.setProperty("preload.distance.km", "2.0");
        props.setProperty("max.cache.age.ms", "3600000");
        
        // Try to load from file if it exists
        try (FileInputStream fis = new FileInputStream("cache_config.properties")) {
            props.load(fis);
            LOGGER.info("Loaded cache configuration from cache_config.properties");
        } catch (IOException e) {
            LOGGER.info("Using default cache configuration (cache_config.properties not found)");
        }
        
        return props;
    }
    
    /**
     * Generate a unique cache key from location and radius
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param radiusKm Radius
     * @return Cache key string
     */
    private String generateCacheKey(double lat, double lng, double radiusKm) {
        double roundedLat = Math.round(lat * 10000.0) / 10000.0;
        double roundedLng = Math.round(lng * 10000.0) / 10000.0;
        double roundedRadius = Math.round(radiusKm * 100.0) / 100.0;
    
        return String.format("%.4f_%.4f_%.2f", roundedLat, roundedLng, roundedRadius);
    }
    
    /**
     * Cache road data for a specific location Fetches from API if not already cached
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param radiusKm Radius to fetch data for
     */
    public void cacheRoadData(double lat, double lng, double radiusKm) {
        String key = generateCacheKey(lat, lng, radiusKm);

        if (cache.containsKey(key)) {
            CachedMapData existing = cache.get(key);
            if (existing.isValid(maxCacheAgeMs)) {
                LOGGER.fine(String.format("Data already cached for key: %s", key));
                return;
            } else {
                LOGGER.fine(String.format("Cached data expired for key: %s", key));
                cache.remove(key);
            }
        }
        
        if (cache.size() >= maxCacheSize) {
            evictOldEntries();
        }
        
        try {
            //gets data from API
            LOGGER.info(String.format("Fetching road data from API: lat=%.4f, lng=%.4f, radius=%.2fkm",
                    lat, lng, radiusKm));
            
            List<RoadSegment> roads = apiController.getRoadData(lat, lng, radiusKm);
            StreetViewImage image = apiController.getStreetViewImage(lat, lng, 0);
            
            totalFetches++;
            
            //create cached data object
            CachedMapData data = new CachedMapData(lat, lng, radiusKm);
            if (roads != null) {
                data.setRoadSegments(roads);
            }
            if (image != null) {
                data.addImage(image);
            }
            
            //store in cache
            cache.put(key, data);
            
            LOGGER.info(String.format("Cached data for key: %s (roads=%d, images=%d)",
                    key, data.getRoadSegments().size(), data.getImages().size()));
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to cache road data: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get cached data for a location
     * Returns null if not cached or data is expired
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param radiusKm Radius
     * @return Cached data or null if not cached
     */
    public CachedMapData getCachedData(double lat, double lng, double radiusKm) {
        String key = generateCacheKey(lat, lng, radiusKm);
        CachedMapData data = cache.get(key);
        
        if (data != null && data.isValid(maxCacheAgeMs)) {
            cacheHits++;
            LOGGER.fine(String.format("Cache HIT for key: %s", key));
            return data;
        } else {
            cacheMisses++;
            LOGGER.fine(String.format("Cache MISS for key: %s", key));
            if (data != null) {
                cache.remove(key);
            }
            return null;
        }
    }
    
    /**
     * Evict old entries when cache is full
     */
    private void evictOldEntries() {
        if (cache.size() <= maxCacheSize) {
            return;
        }
        
        int toRemove = cache.size() - maxCacheSize + 1;
        
        //sort entries by timestamp (oldest first)
        List<Map.Entry<String, CachedMapData>> entries = new ArrayList<>(cache.entrySet());
        entries.sort(Comparator.comparingLong(e -> e.getValue().getTimestamp()));
        
        //Remove oldest entries
        int removed = 0;
        for (int i = 0; i < toRemove && i < entries.size(); i++) {
            String key = entries.get(i).getKey();
            cache.remove(key);
            removed++;
        }
        
        LOGGER.info(String.format("Evicted %d old cache entries (cache size: %d)", removed, cache.size()));
    }
    
    /**
     * Preload map data along a route
     * Caches data for waypoints and intermediate points within preloadDistanceKm ahead
     *
     * @param waypoints List of waypoints along the route
     */
    public void preloadAlongPath(List<Waypoint> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            LOGGER.warning("Cannot preload: waypoints list is empty");
            return;
        }

        LOGGER.fine(String.format("Preloading data for %d waypoints (distance: %.1fkm)",
                waypoints.size(), preloadDistanceKm));

        for (Waypoint waypoint : waypoints) {
            //Preload data at this waypoint
            cacheRoadData(
                    waypoint.getLatitude(),
                    waypoint.getLongitude(),
                    preloadDistanceKm
            );
        }

        LOGGER.fine("Preloading complete");
    }

    /**
     * Preload road data ahead of a moving vehicle
     * It generates waypoints based on current position, heading, and speed, then
     * preloads road data at those locations.
     *
     * @param currentLat Current latitude
     * @param currentLon Current longitude
     * @param headingDegrees Direction in degrees (0=North, 90=East, 180=South, 270=West)
     * @param speedKmh Current speed in km/h
     */
    public void preloadForVehicle(double currentLat, double currentLon, int headingDegrees, float speedKmh) {
        // Only preload if moving at reasonable speed
        if (speedKmh < 10f) {
            return;
        }

        // Generate waypoints ahead based on vehicle heading
        List<Waypoint> waypoints = generateWaypointsAhead(currentLat, currentLon, headingDegrees);

        // Preload asynchronously
        preloadAlongPath(waypoints);
    }

    /**
     * Generate waypoints ahead of current position based on heading
     * Creates waypoints at 500m, 1km, 1.5km, and 2km ahead
     *
     * @param startLat Starting latitude
     * @param startLon Starting longitude
     * @param headingDeg Heading in degrees from north
     * @return List of waypoints ahead
     */
    private List<Waypoint> generateWaypointsAhead(double startLat, double startLon, int headingDeg) {
        List<Waypoint> waypoints = new ArrayList<>();

        // Add current position
        waypoints.add(new Waypoint(startLat, startLon));

        // Calculate positions ahead (every 500m for next 2km)
        double headingRad = Math.toRadians(headingDeg);
        int numWaypoints = 4; // 0.5, 1.0, 1.5, 2.0 km
        double distancePerWaypoint = 0.0005; // ~500m in degrees (approximate)

        for (int i = 1; i <= numWaypoints; i++) {
            double distance = distancePerWaypoint * i;
            double newLat = startLat + Math.cos(headingRad) * distance;
            double newLon = startLon + Math.sin(headingRad) * distance;
            waypoints.add(new Waypoint(newLat, newLon));
        }

        return waypoints;
    }

    /**
     * clear all cached data
     */
    public void clearCache() {
        cache.clear();
        LOGGER.info("Cache cleared");
    }
    
    /**
     * Get cache hit rate
     * @return Hit rate
     */
    public double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHits / total;
    }
    
    /**
     * @return Number of cached entries
     */
    public int getCacheSize() {
        return cache.size();
    }
    
    /**
     * @return Statistics string
     */
    public String getCacheStats() {
        return String.format(
                "Cache Stats - Size: %d/%d, Hits: %d, Misses: %d, Hit Rate: %.2f%%, Total API Fetches: %d",
                getCacheSize(), maxCacheSize, cacheHits, cacheMisses, getCacheHitRate() * 100, totalFetches
        );
    }
    
    /**
     * Shutdown the cache manager and release resources
     */
    public void shutdown() {
        LOGGER.info("CacheManager shutdown complete");
    }
    
    // Getters for configuration
    public int getMaxCacheSize() {
        return maxCacheSize;
    }
    
    public void setMaxCacheSize(int maxCacheSize) {
        this.maxCacheSize = maxCacheSize;
    }
    
    public double getPreloadDistanceKm() {
        return preloadDistanceKm;
    }
    
    public void setPreloadDistanceKm(double preloadDistanceKm) {
        this.preloadDistanceKm = preloadDistanceKm;
    }
    
    public long getCacheHits() {
        return cacheHits;
    }
    
    public long getCacheMisses() {
        return cacheMisses;
    }
    
    public long getTotalFetches() {
        return totalFetches;
    }
}
