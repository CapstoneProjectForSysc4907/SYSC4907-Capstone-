package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;
import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.caching.CacheManager;
import group7.capstone.caching.CachedMapData;
import group7.capstone.caching.GoogleMapsAPIAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TechnicalSubsystemController {

    private final MapObject world;
    private final CarObject car;
    private final RoadPipelineController roadPipeline;

    // Injected external API controller (cannot modify it)
    private final GoogleMapsAPIController googleApi;

    // Cache (hidden behind the scenes)
    private final CacheManager cacheManager;
    private static final double PRELOAD_DISTANCE_KM = 2.0;

    private List<PhysicsRoadSegment> activeRouteSegments = Collections.emptyList();

    // ---- “need more road” policy ----
    private float roadRequestCooldown = 0f;
    private boolean roadRequestInFlight = false;

    // Tune these
    private static final float NEED_MORE_THRESHOLD_M = 60f; // when remaining < this, request more
    private static final float REQUEST_COOLDOWN_S = 1.5f;   // avoid spamming

    public TechnicalSubsystemController(GoogleMapsAPIController googleApi) {
        this.googleApi = googleApi;

        // Initialize cache behind the scenes
        GoogleMapsAPIAdapter adapter = new GoogleMapsAPIAdapter(googleApi);
        this.cacheManager = new CacheManager(adapter);

        this.world = new MapObject();
        this.car = new CarObject("Car_01", world);
        this.roadPipeline = new RoadPipelineController(2, 3.7f);
    }

    /**
     * Same as your update, but ALSO requests more road when needed.
     * This keeps the “API orchestration” in the controller (system boundary).
     */
    public void updateAndMaybeRequestMoreRoad(float throttle, float brake, float steering, float dt) {
        // 1) physics update
        car.update(throttle, brake, steering, dt);
        world.step(dt);

        // 2) cooldown bookkeeping
        roadRequestCooldown = Math.max(0f, roadRequestCooldown - dt);

        // Preload ahead (handled by cache)
        if (car.isOnRoad()) {
            PhysicsRoadSegment seg = car.getCurrentSegment();
            if (seg != null && seg.getOriginalSegment() != null) {
                cacheManager.preloadForVehicle(
                        seg.getOriginalSegment().getLatitude(),
                        seg.getOriginalSegment().getLongitude(),
                        car.getHeadingDegrees(),
                        car.getSpeed()
                );
            }
        }

        // 3) decide if we need more road
        if (shouldRequestMoreRoadInternal()) {
            requestMoreRoadNow();
        }
    }

    // If you still want a pure physics update, keep this:
    public void update(float throttle, float brake, float steering, float dt) {
        car.update(throttle, brake, steering, dt);
        world.step(dt);
        roadRequestCooldown = Math.max(0f, roadRequestCooldown - dt);
    }

    // --- route set / extend ---

    public void setRouteFromApi(APIResponseDomain response) {
        roadPipeline.runFromApiResponse(response);
        activeRouteSegments = roadPipeline.getPhysicsSegments();
        car.setRouteSegments(activeRouteSegments);

        roadRequestInFlight = false;
        roadRequestCooldown = 0f;

        // Cache this data
        cacheRouteData(response);
    }

    public void extendRouteFromApi(APIResponseDomain response) {
        roadPipeline.appendFromApiResponse(response);
        activeRouteSegments = roadPipeline.getPhysicsSegments();
        car.setRouteSegments(activeRouteSegments);

        // success -> cooldown
        roadRequestInFlight = false;
        roadRequestCooldown = REQUEST_COOLDOWN_S;

        // Cache this data
        cacheRouteData(response);
    }

    // --- “need more road” logic ---

    private boolean shouldRequestMoreRoadInternal() {
        if (roadRequestInFlight) return false;
        if (roadRequestCooldown > 0f) return false;

        // must have a route
        if (activeRouteSegments == null || activeRouteSegments.isEmpty()) return false;

        // must be on-road and have a segment
        if (!car.isOnRoad()) return false;
        PhysicsRoadSegment seg = car.getCurrentSegment();
        if (seg == null) return false;

        float remaining = car.getRemainingRoadMeters();
        return remaining < NEED_MORE_THRESHOLD_M;
    }

    private void requestMoreRoadNow() {
        // Mark as in-flight BEFORE calling
        roadRequestInFlight = true;

        PhysicsRoadSegment seg = car.getCurrentSegment();
        if (seg == null || seg.getOriginalSegment() == null) {
            // Can't compute a sensible lat/lon
            roadRequestInFlight = false;
            roadRequestCooldown = REQUEST_COOLDOWN_S;
            return;
        }

        double lat = seg.getOriginalSegment().getLatitude();
        double lon = seg.getOriginalSegment().getLongitude();
        int headDeg = car.getHeadingDegrees();

        // Try cache first
        CachedMapData cachedData = cacheManager.getCachedData(lat, lon, PRELOAD_DISTANCE_KM);

        if (cachedData != null && !cachedData.getRoadSegments().isEmpty()) {
            // Cache hit: use cached data
            APIResponseDomain response = convertCachedToApiResponse(cachedData);
            extendRouteFromApi(response);
        } else {
            // Cache miss: call API
            APIResponseDomain more = googleApi.getStreet(lat, lon, headDeg);
            extendRouteFromApi(more);
        }
    }

    // --- Cache helper methods ---

    private void cacheRouteData(APIResponseDomain response) {
        if (response == null || response.getSnappedPoints() == null) return;

        for (APIResponseDomain.SnappedPoint point : response.getSnappedPoints()) {
            if (point.getLocation() != null) {
                double lat = point.getLocation().getLatitude();
                double lon = point.getLocation().getLongitude();
                cacheManager.cacheRoadData(lat, lon, PRELOAD_DISTANCE_KM);
            }
        }
    }

    private APIResponseDomain convertCachedToApiResponse(CachedMapData cachedData) {
        APIResponseDomain response = new APIResponseDomain();
        List<APIResponseDomain.SnappedPoint> snappedPoints = new ArrayList<>();

        if (!cachedData.getRoadSegments().isEmpty()) {
            group7.capstone.caching.RoadSegment roadSeg = cachedData.getRoadSegments().get(0);

            for (group7.capstone.caching.RoadSegment.Point point : roadSeg.getPoints()) {
                APIResponseDomain.SnappedPoint snapped = new APIResponseDomain.SnappedPoint();
                APIResponseDomain.LatLng latLng = new APIResponseDomain.LatLng();

                latLng.setLatitude(point.getLatitude());
                latLng.setLongitude(point.getLongitude());
                snapped.setLocation(latLng);

                snappedPoints.add(snapped);
            }
        }

        response.setSnappedPoints(snappedPoints);
        return response;
    }

    // --- existing getters / helpers ---

    public List<PhysicsRoadSegment> getActiveRouteSegments() { return activeRouteSegments; }

    public float getSpeedKmh() { return car.getSpeed(); }
    public String getPositionString() { return car.getPosition().toString(); }
    public String getOrientationString() { return car.getCompassDirection(); }
    public Vector3f getPosition() { return car.getPosition(); }

    public float getStopDistance() { return car.getStopDistance(); }
    public boolean isOnRoad() { return car.isOnRoad(); }

    public int getGeoPointCount() { return roadPipeline.getGeoPoints().size(); }
    public int getPhysicsSegmentCount() { return roadPipeline.getPhysicsSegments().size(); }

    public float getRemainingRoadMeters() { return car.getRemainingRoadMeters(); }
    public int getHeadingDegrees() { return car.getHeadingDegrees(); }
}
