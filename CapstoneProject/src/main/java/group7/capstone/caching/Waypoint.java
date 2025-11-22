package group7.capstone.caching;

/**
 * Represents a waypoint along a route for cache preloading purposes.
 * Includes distance calculation using Haversine formula (haven't made alot of research on it yet, just copied the formula).
 */
public class Waypoint {
    private final double latitude;
    private final double longitude;
    
    public Waypoint(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public double getLatitude() {
        return latitude;
    }
    
    public double getLongitude() {
        return longitude;
    }
    
    /**
     * Calculate distance to another waypoint in kilometers
     */
    public double distanceTo(Waypoint other) {
        final double R = 6371.0;
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLng = Math.toRadians(other.longitude - this.longitude);
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
    
    @Override
    public String toString() {
        return String.format("Waypoint[lat=%.4f, lng=%.4f]", latitude, longitude);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Waypoint other = (Waypoint) obj;
        return Double.compare(latitude, other.latitude) == 0 &&
               Double.compare(longitude, other.longitude) == 0;
    }
    
    @Override
    public int hashCode() {
        long latBits = Double.doubleToLongBits(latitude);
        long lngBits = Double.doubleToLongBits(longitude);
        return 31 * (int)(latBits ^ (latBits >>> 32)) + (int)(lngBits ^ (lngBits >>> 32));
    }
}
