package group7.capstone.caching;

import java.util.ArrayList;
import java.util.List;

/**
 * Data structure representing cached map data for a specific location.
 * TODO: When Action 3 is complete the RoadSegment and StreetViewImage classes 
 * used here will be replaced with the real implementations
 */
public class CachedMapData {
    private final double latitude;
    private final double longitude;
    private final double radiusKm;
    private final List<RoadSegment> roadSegments;
    private final List<StreetViewImage> images;
    private final long timestamp;
    
    public CachedMapData(double latitude, double longitude, double radiusKm) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = radiusKm;
        this.roadSegments = new ArrayList<>();
        this.images = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public double getRadiusKm() {
        return radiusKm;
    }
    
    public List<RoadSegment> getRoadSegments() {
        return roadSegments;
    }
    
    public List<StreetViewImage> getImages() {
        return images;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    // Methods to add data
    public void addRoadSegment(RoadSegment segment) {
        this.roadSegments.add(segment);
    }
    
    public void addImage(StreetViewImage image) {
        this.images.add(image);
    }
    
    public void setRoadSegments(List<RoadSegment> segments) {
        this.roadSegments.clear();
        if (segments != null) {
            this.roadSegments.addAll(segments);
        }
    }
    
    public void setImages(List<StreetViewImage> images) {
        this.images.clear();
        if (images != null) {
            this.images.addAll(images);
        }
    }
    
    /**
     * Check if this cached data is still valid
     * @param maxAgeMs Maximum age in milliseconds
     * @return true if data is still valid
     */
    public boolean isValid(long maxAgeMs) {
        long age = System.currentTimeMillis() - timestamp;
        return age < maxAgeMs;
    }
    
    @Override
    public String toString() {
        return String.format("CachedMapData[lat=%.4f, lng=%.4f, roads=%d, images=%d, age=%dms]",
                latitude, longitude, roadSegments.size(), images.size(),
                System.currentTimeMillis() - timestamp);
    }
}
