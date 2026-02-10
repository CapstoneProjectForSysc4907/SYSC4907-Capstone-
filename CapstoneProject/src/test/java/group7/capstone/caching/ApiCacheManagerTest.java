package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;


class ApiCacheManagerTest {
    @Test
    @DisplayName("API Called Once Then Cache Hit")
    void realApi_calledOnce_then_cacheHit() {
        CountingGoogleMapsAPIController countingApi = new CountingGoogleMapsAPIController(new GoogleMapsAPIController());

        RoadApiCacheManager cache = new RoadApiCacheManager(countingApi);
        cache.setMaxCacheSize(100);
        cache.setMaxAgeMs(60_000L);


        APIResponseDomain a = cache.getStreet(45.4215, -75.6972, 100);//getStreet Calls real API
        assertTrue(countingApi.getStreetCalls >= 1, "Expected at least 1 real API call");

        int callsAfterFirst = countingApi.getStreetCalls;
        APIResponseDomain b = cache.getStreet(45.4215, -75.6972, 100);
        assertEquals(callsAfterFirst, countingApi.getStreetCalls,"no additional API call on second request");
    }

    /**
     * Wrapper that counts real network calls.
     */
    private static class CountingGoogleMapsAPIController extends GoogleMapsAPIController {
        private final GoogleMapsAPIController inner;
        int getStreetCalls = 0;

        CountingGoogleMapsAPIController(GoogleMapsAPIController inner) {
            this.inner = inner;
        }

        @Override
        public APIResponseDomain getStreet(double lat, double lon, int head) {
            getStreetCalls++;
            return inner.getStreet(lat, lon, head);
        }
    }
}
