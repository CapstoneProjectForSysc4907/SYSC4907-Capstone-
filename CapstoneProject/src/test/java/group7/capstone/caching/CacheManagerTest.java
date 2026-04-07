package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CacheManagerTest {

    @Test
    void sameRequestUsesCache() {
        FakeGoogleMapsApi api = new FakeGoogleMapsApi();
        RoadApiCacheManager cacheManager = new RoadApiCacheManager(api);

        APIResponseDomain first = cacheManager.getStreet(45.4215, -75.6972, 90);
        APIResponseDomain second = cacheManager.getStreet(45.4215, -75.6972, 90);

        assertSame(first, second);
        assertEquals(1, api.getStreetCalls);
        assertTrue(cacheManager.getStats().contains("hits=1"));
    }

    @Test
    void expiredEntryCallsApiAgain() throws InterruptedException {
        FakeGoogleMapsApi api = new FakeGoogleMapsApi();
        RoadApiCacheManager cacheManager = new RoadApiCacheManager(api);
        cacheManager.setMaxAgeMs(1);

        cacheManager.getStreet(45.4215, -75.6972, 90);
        Thread.sleep(5);
        cacheManager.getStreet(45.4215, -75.6972, 90);

        assertEquals(2, api.getStreetCalls);
    }

    @Test
    void normalizedHeadingUsesSameCacheKey() {
        FakeGoogleMapsApi api = new FakeGoogleMapsApi();
        RoadApiCacheManager cacheManager = new RoadApiCacheManager(api);

        cacheManager.getStreet(45.4215, -75.6972, 370);
        cacheManager.getStreet(45.4215, -75.6972, 10);

        assertEquals(1, api.getStreetCalls);
    }

    private static class FakeGoogleMapsApi extends GoogleMapsAPIController {
        int getStreetCalls = 0;

        @Override
        public APIResponseDomain getStreet(double lat, double lon, int head) {
            getStreetCalls++;

            APIResponseDomain response = new APIResponseDomain();
            APIResponseDomain.SnappedPoint point = new APIResponseDomain.SnappedPoint();
            APIResponseDomain.LatLng location = new APIResponseDomain.LatLng();

            location.setLatitude(lat);
            location.setLongitude(lon);
            point.setLocation(location);
            point.setPlaceId("test-place-" + getStreetCalls);

            response.setSnappedPoints(java.util.List.of(point));
            return response;
        }
    }
}
