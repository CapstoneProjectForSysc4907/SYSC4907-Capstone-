package group7.capstone.caching;

/**
 * This is a stub that Will be replaced when Action 3 API Controller is done
 */
public class RoadSegment {
    private final double latitude;
    private final double longitude;
    private final double speedLimit;
    private final String roadName;
    
    /**
     * TODO: When the real Google Maps API Controller is built,
     * update this constructor to match the actual API response
     */
    public RoadSegment(double latitude, double longitude, double speedLimit, String roadName) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedLimit = speedLimit;
        this.roadName = roadName;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public double getSpeedLimit() {
        return speedLimit;
    }
    
    public String getRoadName() {
        return roadName;
    }
    
    @Override
    public String toString() {
        return String.format("RoadSegment[lat=%.4f, lng=%.4f, speed=%.0f km/h, name=%s]",
                latitude, longitude, speedLimit, roadName);
    }
}
