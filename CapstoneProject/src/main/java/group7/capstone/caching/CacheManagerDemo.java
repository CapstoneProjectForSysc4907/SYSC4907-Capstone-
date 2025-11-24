package group7.capstone.caching;

import java.util.*;

/**
 * Demo for CacheManager
 * 
 * TODO: When Action 3 is complete, you can create a similar demo
 * that uses the real Google Maps API Controller to see the cache working with
 * real data from Google Maps.
 */
public class CacheManagerDemo {
    
    public static void main(String[] args) {
        System.out.println("=== ACTION 4: Cache Manager Demo ===");
        System.out.println("NOTE: Using MOCK API data\n");
        
        //Create mock API controller (simulates what Action 3 will provide)
        MockAPIController mockApi = new MockAPIController();
        
        CacheManager cacheManager = new CacheManager(mockApi);
        
        System.out.println("Cache Manager initialized");
        System.out.println("Max cache size: " + cacheManager.getMaxCacheSize());
        System.out.println("Preload distance: " + cacheManager.getPreloadDistanceKm() + " km\n");
        
        System.out.println("=== Demo 1: Caching Data ===");
        cacheData(cacheManager, 45.4215, -75.6972, 2.0, "Ottawa");
        cacheData(cacheManager, 43.6532, -79.3832, 2.0, "Toronto");
        cacheData(cacheManager, 45.5017, -73.5673, 2.0, "Montreal");
        cacheData(cacheManager, 49.2827, -123.1207, 2.0, "Vancouver");
        cacheData(cacheManager, 51.0447, -114.0719, 2.0, "Calgary");
    
        System.out.println("\n" + cacheManager.getCacheStats() + "\n");
        
        System.out.println("=== Demo 2: Testing Cache Hits ===");
        retrieveCachedData(cacheManager, 45.4215, -75.6972, 2.0, "Ottawa");
        retrieveCachedData(cacheManager, 43.6532, -79.3832, 2.0, "Toronto");
        retrieveCachedData(cacheManager, 45.4215, -75.6972, 2.0, "Ottawa");
        
        System.out.println("\n" + cacheManager.getCacheStats() + "\n");
        
        System.out.println("=== Demo 3: Testing Cache Miss ===");
        retrieveCachedData(cacheManager, 53.5461, -113.4938, 2.0, "Edmonton");
        
        System.out.println("\n" + cacheManager.getCacheStats() + "\n");
        
        System.out.println("=== Demo 4: Testing Cache Eviction ===");
        System.out.println("Adding more cities to trigger eviction");
        cacheData(cacheManager, 49.8951, -97.1384, 2.0, "Winnipeg");
        cacheData(cacheManager, 44.6488, -63.5752, 2.0, "Halifax");
        System.out.println("\nCache size after eviction: " + cacheManager.getCacheSize());
        System.out.println(cacheManager.getCacheStats() + "\n");
        
        System.out.println("=== Demo 5: Preloading Along Route ===");
        List<Waypoint> route = createSampleRoute();
        System.out.println("Preloading data for " + route.size() + " waypoints");
        cacheManager.preloadAlongPath(route);
        System.out.println("Preloading complete");
        System.out.println(cacheManager.getCacheStats() + "\n");
        
        System.out.println("=== Demo 6: Final Statistics ===");
        System.out.println(cacheManager.getCacheStats());
        System.out.println("Total API calls made: " + mockApi.getTotalCalls());
        System.out.println("\nCache efficiency: " + String.format("%.1f%%", cacheManager.getCacheHitRate() * 100));
        
        System.out.println("\n=== Demo Completed ===");
        System.out.println("TODO : Replace MockAPIController with real Google Maps API");
        
        cacheManager.shutdown();
    }
    
    private static void cacheData(CacheManager manager, double lat, double lng, double radius, String location) {
        System.out.println("Caching data for " + location + " (lat=" + lat + ", lng=" + lng + ")");
        manager.cacheRoadData(lat, lng, radius);
    }
    
    private static void retrieveCachedData(CacheManager manager, double lat, double lng, double radius, String location) {
        System.out.print("Retrieving data for " + location);
        CachedMapData data = manager.getCachedData(lat, lng, radius);
        
        if (data != null) {
            System.out.println("HIT (Roads: " + data.getRoadSegments().size() + ", Images: " + data.getImages().size() + ")");
        } else {
            System.out.println("MISS");
        }
    }
    
    private static List<Waypoint> createSampleRoute() {
        List<Waypoint> route = new ArrayList<>();
        
        route.add(new Waypoint(45.4215, -75.6972));// Ottawa
        route.add(new Waypoint(45.3833, -74.7333));// Midpoint
        route.add(new Waypoint(45.5017, -73.5673));// Montreal
        
        return route;
    }
    
    /**
     * MOCK API CONTROLLER
     * this is what action 3 will do, might have to change this later
     * 
     * TODO: Replace this with your real GoogleMapsAPIController
     */
    private static class MockAPIController implements GoogleMapsAPIController {
        private int totalCalls = 0;
        
        @Override
        public List<RoadSegment> getRoadData(double lat, double lng, double radiusKm) {
            totalCalls++;
            System.out.println("Simulating Google Maps API call");
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            //mock data
            List<RoadSegment> segments = new ArrayList<>();
            segments.add(new RoadSegment(lat, lng, 50.0, "Main Street"));
            segments.add(new RoadSegment(lat + 0.01, lng + 0.01, 60.0, "Highway 1"));
            segments.add(new RoadSegment(lat - 0.01, lng + 0.01, 40.0, "Blues Avenue"));
            
            return segments;
        }
        
        @Override
        public StreetViewImage getStreetViewImage(double lat, double lng, int heading) {
            totalCalls++;
            System.out.println(" Simulating Street View API call");
            
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            //return mock image data
            byte[] mockImage = new byte[2048]; //2KB mock image
            Arrays.fill(mockImage, (byte) 0xFF);
            
            return new StreetViewImage(mockImage, heading, lat, lng);
        }
        
        public int getTotalCalls() {
            return totalCalls;
        }
    }
}
