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

/**
 * Manages loading, caching, and validation of Street View images
 */
public class ImageLoader {

    private final GoogleMapsAPIController apiController;
    private final Map<String, BufferedImage> imageCache;
    private final Map<String, Long> imageCacheTimestamps;

    private int maxCacheSize;
    private BufferedImage placeholderImage;

    // stats
    private long cacheHits;
    private long cacheMisses;
    private long successfulLoads;
    private long failedLoads;
    private int currentlyLoading = 0;

    private final ExecutorService executorService;

    public ImageLoader(GoogleMapsAPIController apiController) {
        this.apiController = apiController;
        this.imageCache = new LinkedHashMap<>();
        this.imageCacheTimestamps = new HashMap<>();
        this.maxCacheSize = 50;
        this.cacheHits = 0;
        this.cacheMisses = 0;
        this.successfulLoads = 0;
        this.failedLoads = 0;
        this.executorService = Executors.newFixedThreadPool(4);

        createPlaceholderImage();
    }

    private void createPlaceholderImage() {
        placeholderImage = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);

        // fill with gray
        for (int y = 0; y < placeholderImage.getHeight(); y++) {
            for (int x = 0; x < placeholderImage.getWidth(); x++) {
                placeholderImage.setRGB(x, y, 0xCCCCCC);
            }
        }
    }

    private String generateCacheKey(double lat, double lng, int heading) {
        double roundedLat = Math.round(lat * 10000.0) / 10000.0;
        double roundedLng = Math.round(lng * 10000.0) / 10000.0;
        return String.format("%.4f_%.4f_%d", roundedLat, roundedLng, heading);
    }

    public BufferedImage loadStreetViewImage(double lat, double lng, int heading) {
        String cacheKey = generateCacheKey(lat, lng, heading);

        // check cache first
        if (imageCache.containsKey(cacheKey)) {
            cacheHits++;
            return imageCache.get(cacheKey);
        }

        cacheMisses++;

        try {
            StreetViewImage apiImage = apiController.GetStreetViewImage(lat, lng, heading);

            if (apiImage == null) {
                failedLoads++;
                return getPlaceholderImage();
            }

            BufferedImage image = apiImage.getImage();

            if (image == null) {
                failedLoads++;
                return getPlaceholderImage();
            }

            if (!isImageValid(image)) {
                failedLoads++;
                return getPlaceholderImage();
            }

            BufferedImage formattedImage = formatImageForGUI(image);
            storeInCache(cacheKey, formattedImage);
            successfulLoads++;

            return formattedImage;

        } catch (IOException e) {
            failedLoads++;
            return getPlaceholderImage();
        }
    }

    public BufferedImage loadMapImage(double lat, double lng, int heading) {
        String cacheKey = generateCacheKey(lat, lng, heading);

        if (imageCache.containsKey(cacheKey)) {
            cacheHits++;
            return imageCache.get(cacheKey);
        }

        cacheMisses++;

        try {
            BufferedImage image = apiController.GetMapImage(lat, lng);

            if (image == null) {
                failedLoads++;
                return getPlaceholderImage();
            }

            if (!isImageValid(image)) {
                failedLoads++;
                return getPlaceholderImage();
            }

            BufferedImage formattedImage = formatImageForGUI(image);
            storeInCache(cacheKey, formattedImage);
            successfulLoads++;
            return formattedImage;

        } catch (IOException e) {
            failedLoads++;
            return getPlaceholderImage();
        }
    }

    // async version
    public CompletableFuture<BufferedImage> loadImageAsync(double lat, double lng, int heading,
                                                           Consumer<BufferedImage> callback) {
        currentlyLoading++;

        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadStreetViewImage(lat, lng, heading);
            } catch (Exception e) {
                return getPlaceholderImage();
            } finally {
                currentlyLoading--;
            }
        }, executorService).thenApply(image -> {
            if (callback != null) {
                callback.accept(image);
            }
            return image;
        });
    }

    public CompletableFuture<BufferedImage> loadMapAsync(double lat, double lng, int heading,
                                                         Consumer<BufferedImage> callback) {
        currentlyLoading++;

        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadMapImage(lat, lng, heading);
            } catch (Exception e) {
                return getPlaceholderImage();
            } finally {
                currentlyLoading--;
            }
        }, executorService).thenApply(image -> {
            if (callback != null) {
                callback.accept(image);
            }
            return image;
        });
    }

    private boolean isImageValid(BufferedImage image) {
        if (image == null) return false;
        if (image.getWidth() <= 0 || image.getHeight() <= 0) return false;

        // check if image is completely black (error placeholder)
        int[] pixels = new int[100];
        try {
            image.getRGB(0, 0, 10, 10, pixels, 0, 10);
        } catch (Exception e) {
            return false;
        }

        int blackCount = 0;
        for (int pixel : pixels) {
            if (pixel == 0xFF000000) blackCount++;
        }

        return blackCount != pixels.length;
    }

    private BufferedImage formatImageForGUI(BufferedImage image) {
        if (image == null) return placeholderImage;

        int targetWidth = 800;
        int targetHeight = 600;

        BufferedImage scaledImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g2d = scaledImage.createGraphics();
        g2d.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        g2d.dispose();

        return scaledImage;
    }

    private void storeInCache(String key, BufferedImage image) {
        if (imageCache.size() >= maxCacheSize) {
            evictOldestImage();
        }

        imageCache.put(key, image);
        imageCacheTimestamps.put(key, System.currentTimeMillis());
    }

    private void evictOldestImage() {
        if (imageCache.isEmpty()) return;

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

    public BufferedImage getPlaceholderImage() {
        return placeholderImage != null ? placeholderImage :
                new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
    }

    public BufferedImage getCachedImage(double lat, double lng, int heading) {
        String key = generateCacheKey(lat, lng, heading);
        return imageCache.get(key);
    }

    public void clearCache() {
        imageCache.clear();
        imageCacheTimestamps.clear();
    }

    public int getLoadingCount() { return currentlyLoading; }
    public int getCacheSize() { return imageCache.size(); }

    public double getCacheHitRate() {
        long total = cacheHits + cacheMisses;
        if (total == 0) return 0.0;
        return (double) cacheHits / total;
    }

    public String getImageLoadStats() {
        return String.format(
                "Cache: %d/%d (Hit Rate: %.1f%%), Successful: %d, Failed: %d, Currently Loading: %d",
                getCacheSize(), maxCacheSize, getCacheHitRate() * 100,
                successfulLoads, failedLoads, currentlyLoading
        );
    }

    public void setMaxCacheSize(int size) {
        this.maxCacheSize = size;
        while (imageCache.size() > maxCacheSize) {
            evictOldestImage();
        }
    }
    //statistics getters
    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public long getSuccessfulLoads() { return successfulLoads; }
    public long getFailedLoads() { return failedLoads; }

    public void shutdown() {
        executorService.shutdown();
        clearCache();
    }
}
