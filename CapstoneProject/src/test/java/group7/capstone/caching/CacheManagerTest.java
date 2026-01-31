package group7.capstone.caching;

import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CacheManager
 * 
 * TODO: When Action 3 is complete I'll add additional integration
 * These unit tests will continue to work with the mock.
 */
class CacheManagerTest {
    
    private CacheManager cacheManager;
    private MockGoogleMapsAPIController mockApiController;
    
    @BeforeEach
    void setUp() {
        //Create mock API controller
        mockApiController = new MockGoogleMapsAPIController();
        
        //Create cache manager with test config
        Properties testConfig = new Properties();
        testConfig.setProperty("max.cache.size", "5");
        testConfig.setProperty("preload.distance.km", "2.0");
        testConfig.setProperty("max.cache.age.ms", "60000"); // 1 minute for testing
        
        cacheManager = new CacheManager(mockApiController, testConfig);
    }
    
    @AfterEach
    void tearDown() {
        if (cacheManager != null) {
            cacheManager.shutdown();
        }
    }
    
    @Test
    @DisplayName("Test Cache Hit: Add data to cache and retrieve it")
    void testCacheHit() {
        //cache data for a location
        double lat = 45.4215;
        double lng = -75.6972;
        double radius = 2.0;
        
        cacheManager.cacheRoadData(lat, lng, radius);
        
        //retrieve cached data
        CachedMapData data = cacheManager.getCachedData(lat, lng, radius);

        assertNotNull(data, "Cached data should not be null");
        assertEquals(lat, data.getLatitude(), 0.0001);
        assertEquals(lng, data.getLongitude(), 0.0001);
        assertTrue(data.getRoadSegments().size() > 0, "Should have road segments");
        assertEquals(1, cacheManager.getCacheHits(), "Should have 1 cache hit");
        assertEquals(0, cacheManager.getCacheMisses(), "Should have 0 cache misses");
    }
    
    @Test
    @DisplayName("Test Cache Miss: Try to get data that doesn't exist")
    void testCacheMiss() {
        //Try to get data that was never cached
        CachedMapData data = cacheManager.getCachedData(45.0, -75.0, 2.0);

        assertNull(data, "Should return null for uncached location");
        assertEquals(0, cacheManager.getCacheHits(), "Should have 0 cache hits");
        assertEquals(1, cacheManager.getCacheMisses(), "Should have 1 cache miss");
    }
    
    @Test
    @DisplayName("Test: Cache eviction when exceeding max size")
    void testEviction() {
        //max cache size is 5 in test config so addting 7 entries should trigger eviction
        for (int i = 0; i < 7; i++) {
            double lat = 45.0 + i * 0.1;
            double lng = -75.0 + i * 0.1;
            cacheManager.cacheRoadData(lat, lng, 2.0);
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        //Cache should be at max size or slightly below after eviction
        assertTrue(cacheManager.getCacheSize() <= 5, "Cache size should be at or below max after eviction");
        
        //first two oldest entries should have been evicted
        CachedMapData first = cacheManager.getCachedData(45.0, -75.0, 2.0);
        CachedMapData second = cacheManager.getCachedData(45.1, -75.1, 2.0);
        
        assertNull(first, "Oldest entry should be evicted");
        assertNull(second, "Second oldest entry should be evicted");
        
        //one of the newer entries should still exist
        CachedMapData lastAdded = cacheManager.getCachedData(45.6, -74.4, 2.0);
        assertNotNull(lastAdded, "Most recent entry should still be cached");
    }
    
    @Test
    @DisplayName("Test: Multiple retrievals of same data")
    void testMultipleCacheHits() {
        double lat = 45.4215;
        double lng = -75.6972;
        double radius = 2.0;
        
        //Cache once
        cacheManager.cacheRoadData(lat, lng, radius);
        
        //Retrieve multiple times
        for (int i = 0; i < 5; i++) {
            CachedMapData data = cacheManager.getCachedData(lat, lng, radius);
            assertNotNull(data, "Should retrieve cached data");
        }
        
        //Should have 5 hits and 0 misses
        assertEquals(5, cacheManager.getCacheHits());
        assertEquals(0, cacheManager.getCacheMisses());
        
        //API should be  twice during init caching once for roads and once for image
        assertEquals(2, mockApiController.getCallCount());
    }
    
    @Test
    @DisplayName("Test: Cache hit rate calculation")
    void testCacheHitRate() {
        assertEquals(0.0, cacheManager.getCacheHitRate(), 0.01);
        cacheManager.cacheRoadData(45.0, -75.0, 2.0);
        cacheManager.cacheRoadData(46.0, -76.0, 2.0);
        
        //3 hits
        cacheManager.getCachedData(45.0, -75.0, 2.0);
        cacheManager.getCachedData(45.0, -75.0, 2.0);
        cacheManager.getCachedData(46.0, -76.0, 2.0);
        
        //2 misses
        cacheManager.getCachedData(47.0, -77.0, 2.0);
        cacheManager.getCachedData(48.0, -78.0, 2.0);
        
        //Hit rate should be 3/5 = 0.6
        assertEquals(0.6, cacheManager.getCacheHitRate(), 0.01);
    }
    
    @Test
    @DisplayName("Test: Preload along path")
    void testPreloadAlongPath() {
        //Create a simple path with 3 waypoints
        List<Waypoint> waypoints = Arrays.asList(
                new Waypoint(45.4215, -75.6972),//Ottawa
                new Waypoint(45.5017, -73.5673),//Montreal
                new Waypoint(43.6532, -79.3832) //toronto
        );

        cacheManager.preloadAlongPath(waypoints);
        assertTrue(cacheManager.getCacheSize() > 0, 
                "Should have cached data after preloading");
    }
    
    @Test
    @DisplayName("Test: Clear cache")
    void testClearCache() {
        cacheManager.cacheRoadData(45.0, -75.0, 2.0);
        cacheManager.cacheRoadData(46.0, -76.0, 2.0);
        assertEquals(2, cacheManager.getCacheSize());
        cacheManager.clearCache();
        assertEquals(0, cacheManager.getCacheSize(), "Cache should be empty after clear");
    }
    
    @Test
    @DisplayName("Test: Cache statistics")
    void testCacheStats() {
        cacheManager.cacheRoadData(45.0, -75.0, 2.0);
        cacheManager.getCachedData(45.0, -75.0, 2.0); // hit
        cacheManager.getCachedData(46.0, -76.0, 2.0); // miss
        String stats = cacheManager.getCacheStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Size: 1"), "Stats should show cache size");
        assertTrue(stats.contains("Hits: 1"), "Stats should show hit count");
        assertTrue(stats.contains("Misses: 1"), "Stats should show miss count");
    }
    
    @Test
    @DisplayName("Test: Same location with different radius")
    void testDifferentRadius() {
        double lat = 45.4215;
        double lng = -75.6972;
        cacheManager.cacheRoadData(lat, lng, 2.0);
        //retrieve with 2km should hit
        assertNotNull(cacheManager.getCachedData(lat, lng, 2.0));
        //Retrieve with 3km should miss
        assertNull(cacheManager.getCachedData(lat, lng, 3.0));
    }
    
    @Test
    @DisplayName("Test: Similar coordinates get same cache key")
    void testCoordinateRounding() {
        //These coordinates should round to the same cache key
        cacheManager.cacheRoadData(45.42151, -75.69721, 2.0);
        CachedMapData data = cacheManager.getCachedData(45.42149, -75.69719, 2.0);
        assertNotNull(data, "Similar coordinates should use same cache entry");
        assertEquals(1, cacheManager.getCacheHits());
    }
    
    /**
     * TODO: When Action 3 is complete, you can test with the real
     * API controller in integration tests. This mock will remain for unit tests.
     */
    private static class MockGoogleMapsAPIController implements GoogleMapsAPIController {
        private int callCount = 0;
        
        @Override
        public List<RoadSegment> getRoadData(double lat, double lng, double radiusKm) {
            callCount++;
            //mock road segments
            List<RoadSegment> segments = new ArrayList<>();
            //segments.add(new RoadSegment(lat, lng, 50.0, "Mock Street"));
          //  segments.add(new RoadSegment(lat + 0.01, lng + 0.01, 60.0, "Mock Avenue"));
            
            return segments;
        }
        
        @Override
        public StreetViewImage getStreetViewImage(double lat, double lng, int heading) {
            callCount++;
            byte[] mockImageData = new byte[1024];//1KB mock image
            Arrays.fill(mockImageData, (byte) 0xFF);
            
            return new StreetViewImage(mockImageData, heading, lat, lng);
        }
        
        public int getCallCount() {
            return callCount;
        }
    }
}
