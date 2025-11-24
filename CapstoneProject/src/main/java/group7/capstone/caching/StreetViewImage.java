package group7.capstone.caching;

/**
 * This is a stub that Will be replaced when Action 3 API Controller is done
 */
public class StreetViewImage {
    private final byte[] imageData;
    private final int heading;
    private final double latitude;
    private final double longitude;
    
    /**
     * TODO: When the real Google Maps API Controller is built,
     * update this constructor to match the actual API response
     */
    public StreetViewImage(byte[] imageData, int heading, double latitude, double longitude) {
        this.imageData = imageData;
        this.heading = heading;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public byte[] getImageData() {
        return imageData;
    }
    
    public int getHeading() {
        return heading;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    public int getImageSize() {
        return imageData != null ? imageData.length : 0;
    }
    
    @Override
    public String toString() {
        return String.format("StreetViewImage[lat=%.4f, lng=%.4f, heading=%d°, size=%d bytes]",
                latitude, longitude, heading, getImageSize());
    }
}
