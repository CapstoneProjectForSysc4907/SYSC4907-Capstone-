package group7.capstone.visuals;

import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.APIController.StreetViewImage;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages loading, caching, and validation of Street View images
 */
public class ImageLoader {

    private final GoogleMapsAPIController apiController;
    private final Map<String, BufferedImage> imageCache;
    private final Map<String, Long> imageCacheTimestamps;

    //config
    private int maxCacheSize;
    private BufferedImage placeholderImage;

    //stats
    private long cacheHits;
    private long cacheMisses;
    private long successfulLoads;
    private long failedLoads;
    private final AtomicInteger currentlyLoading = new AtomicInteger(0);

    private final ExecutorService executorService;

    /**
     * Constructor
     * @param apiController
     */
    public ImageLoader(GoogleMapsAPIController apiController) {
        this.apiController = apiController;
        this.imageCache = Collections.synchronizedMap(new LinkedHashMap<String, BufferedImage>());
        this.imageCacheTimestamps = Collections.synchronizedMap(new HashMap<>());
        this.maxCacheSize = 50;
        this.cacheHits = 0;
        this.cacheMisses = 0;
        this.successfulLoads = 0;
        this.failedLoads = 0;
        this.executorService = Executors.newFixedThreadPool(4);

        createPlaceholderImage();
    }

    /**
     * Create a placeholder image to show when loading fails
     */
    private void createPlaceholderImage() {
        placeholderImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);

        //fills a gray background
        for (int y = 0; y < placeholderImage.getHeight(); y++) {
            for (int x = 0; x < placeholderImage.getWidth(); x++) {
                placeholderImage.setRGB(x, y, 0xCCCCCC);
            }
        }
    }

    /**
     * Generate cache key from coordinates and heading
     * @param lat Latitude
     * @param lng Longitude
     * @param heading Camera heading
     * @return Cache key string
     */
    private String generateCacheKey(double lat, double lng, int heading) {
        double roundedLat = Math.round(lat * 10000.0) / 10000.0;
        double roundedLng = Math.round(lng * 10000.0) / 10000.0;
        return String.format("%.4f_%.4f_%d", roundedLat, roundedLng, heading);
    }

    /**
     * Load a Street View image from cache or API
     *
     * @param lat Latitude
     * @param lng Longitude
     * @param heading Camera heading (degrees)
     * @return BufferedImage
     */
    public BufferedImage loadStreetViewImage(double lat, double lng, int heading) {
        String cacheKey = generateCacheKey(lat, lng, heading);

        // checks cache first
        if (imageCache.containsKey(cacheKey)) {
            cacheHits++;
            return imageCache.get(cacheKey);
        }

        cacheMisses++;

        try {
            // Fetch the street view image from API
            StreetViewImage apiImage = apiController.GetStreetViewImage(
                    lat,
                    lng,
                    heading
            );

            if (apiImage == null) {
                failedLoads++;
                return getPlaceholderImage();
            }

            BufferedImage image = apiImage.getImage();

            if (image == null) {
                failedLoads++;
                return getPlaceholderImage();
            }

            // Validate image
            if (!isImageValid(image)) {
                failedLoads++;
                return getPlaceholderImage();
            }

            // Format for GUI
            BufferedImage formattedImage = formatImageForGUI(image);

            // Cache the image
            storeInCache(cacheKey, formattedImage);

            successfulLoads++;
            return formattedImage;

        } catch (IOException e) {
            failedLoads++;
            return getPlaceholderImage();
        }
    }

    /**
     * Load a Street View image asynchronously
     *
     * @param lat Latitude
     * @param lng Longitude
     * @param heading Camera heading
     * @param callback Function to call when image is loaded
     * @return CompletableFuture for the loading operation
     */
    public CompletableFuture<BufferedImage> loadImageAsync(double lat, double lng, int heading,
                                                           Consumer<BufferedImage> callback) {
        currentlyLoading.incrementAndGet();

        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadStreetViewImage(lat, lng, heading);
            } catch (Exception e) {
                return getPlaceholderImage();
            } finally {
                currentlyLoading.decrementAndGet();
            }
        }, executorService).thenApply(image -> {
            if (callback != null) {
                callback.accept(image);
            }
            return image;
        });
    }

    /**
     * Validate that an image is legitimate and not corrupted
     *
     * @param image Image to validate
     * @return true if image is valid, false otherwise
     */
    private boolean isImageValid(BufferedImage image) {
        if (image == null) {
            return false;
        }

        if (image.getWidth() <= 0 || image.getHeight() <= 0) {
            return false;
        }

        // Check if image is completely black (error placeholder)
        int[] pixels = new int[100];
        try {
            image.getRGB(0, 0, 10, 10, pixels, 0, 10);
        } catch (Exception e) {
            return false;
        }

        int blackCount = 0;
        for (int pixel : pixels) {
            if (pixel == 0xFF000000) {
                blackCount++;
            }
        }

        return blackCount != pixels.length;
    }

    /**
     * Format image for GUI display (resize and adjust)
     *
     * @param image Original image from API
     * @return Formatted BufferedImage
     */
    private BufferedImage formatImageForGUI(BufferedImage image) {
        if (image == null) {
            return placeholderImage;
        }

        // Target dimensions for GUI display
        int targetWidth = 800;
        int targetHeight = 600;

        // Create scaled version
        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        // Simple scaling using Graphics2D
        java.awt.Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return scaledImage;
    }

    /**
     * Store image in cache with automatic eviction if full
     *
     * @param key Cache key
     * @param image Image to cache
     */
    private void storeInCache(String key, BufferedImage image) {
        if (imageCache.size() >= maxCacheSize) {
            evictOldestImage();
        }

        imageCache.put(key, image);
        imageCacheTimestamps.put(key, System.currentTimeMillis());
    }

    /**
     * Evict the oldest image from cache
     */
    private void evictOldestImage() {
        if (imageCache.isEmpty()) {
            return;
        }

        // Find oldest entry
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;

        for (Map.Entry<String, Long> entry : imageCacheTimestamps.entrySet()) {
            if (entry.getValue() < oldestTime) {
                oldestTime = entry.getValue();
                oldestKey = entry.getKey();
            }
        }

        if (oldestKey != null) {
            imageCache.remove(oldestKey);
            imageCacheTimestamps.remove(oldestKey);
        }
    }

    /**
     * Get the placeholder image for failed loads
     *
     * @return Placeholder BufferedImage
     */
    public BufferedImage getPlaceholderImage() {
        return placeholderImage != null ? placeholderImage :
                new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
    }

    /**
     * Get cached image without loading from API
     *
     * @param lat Latitude
     * @param lng Longitude
     * @param heading Camera heading
     * @return Cached image or null if not in cache
     */
    public BufferedImage getCachedImage(double lat, double lng, int heading) {
        String key = generateCacheKey(lat, lng, heading);
        return imageCache.get(key);
    }

    /**
     * Clear all cached images
     */
    public void clearCache() {
        imageCache.clear();
        imageCacheTimestamps.clear();
    }

    /**
     * Get number of images currently loading
     *
     * @return Number of ongoing asynchronous image loading
     */
    public int getLoadingCount() {
        return currentlyLoading.get();
    }

    /**
     * Get current cache size
     *
     * @return Number of images in cache
     */
    public int getCacheSize() {
        return imageCache.size();
    }

    /**
     * Get cache hit rate
     * @return Hit rate (0.0 to 1.0)
     */
    public double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHits / total;
    }

    /**
     * Get ImageLoader statistics
     *
     * @return Statistics as string
     */
    public String getImageLoadStats() {
        return String.format(
                "Cache: %d/%d (Hit Rate: %.1f%%), " +
                        "Successful: %d, Failed: %d, Currently Loading: %d",
                getCacheSize(),
                maxCacheSize,
                getCacheHitRate() * 100,
                successfulLoads,
                failedLoads,
                currentlyLoading.get()
        );
    }

    /**
     * Set maximum cache size
     *
     * @param size New max cache size
     */
    public void setMaxCacheSize(int size) {
        this.maxCacheSize = size;

        // Evict excess images if cache is now over limit
        while (imageCache.size() > maxCacheSize) {
            evictOldestImage();
        }
    }

    /**
     * Shutdown the image loader, clears cache
     */
    public void shutdown() {
        executorService.shutdown();
        clearCache();
    }

    //statistics getters
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public long getSuccessfulLoads() { return successfulLoads; }
    public long getFailedLoads() { return failedLoads; }
}
