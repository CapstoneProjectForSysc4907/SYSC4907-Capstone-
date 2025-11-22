package group7.capstone.caching;

import java.util.List;

/**
 * This interface defines what the CacheManager needs from the Google Maps API Controller.
 * 
 * TODO: When you implement Action 3 your GoogleMapsAPIController class should implement this interface.
 * 
 * like this:
 * public class GoogleMapsAPIController implements group7.capstone.caching.GoogleMapsAPIController {
 *     @Override
 *     public List<RoadSegment> getRoadData(double lat, double lng, double radiusKm) {
 *         // Your Google Maps API calls here
 *     }
 *     
 *     @Override
 *     public StreetViewImage getStreetViewImage(double lat, double lng, int heading) {
 *         // Your Google Maps API calls here
 *     }
 * }
 */
public interface GoogleMapsAPIController {
    
    /**
     * Fetch road data for a given location and radius
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param radiusKm Radius in kilometers
     * @return List of road segments, or empty list if none found
     */
    List<RoadSegment> getRoadData(double lat, double lng, double radiusKm);
    
    /**
     * Fetch a Street View image at a specific location and heading
     * 
     * @param lat Latitude
     * @param lng Longitude
     * @param heading Direction in degrees (0-360)
     * @return StreetViewImage or null if unavailable
     */
    StreetViewImage getStreetViewImage(double lat, double lng, int heading);
}
