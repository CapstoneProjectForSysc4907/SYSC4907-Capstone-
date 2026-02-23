package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;
import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.caching.RoadApiCacheManager;

import java.util.Collections;
import java.util.List;

public class TechnicalSubsystemController {

    private final MapObject world;
    private final CarObject car;
    private final RoadPipelineController roadPipeline;

    // Injected external API controller (cannot modify it)
    private final GoogleMapsAPIController googleApi;

    // Optional cache wrapper for getStreet()
    private final RoadApiCacheManager roadCache;

    private List<PhysicsRoadSegment> activeRouteSegments = Collections.emptyList();

    // ---- “need more road” policy ----
    private float roadRequestCooldown = 0f;
    private boolean roadRequestInFlight = false;

    // Tune these
    private static final float NEED_MORE_THRESHOLD_M = 60f; // when remaining < this, request more
    private static final float REQUEST_COOLDOWN_S = 1.5f;   // avoid spamming


    public TechnicalSubsystemController(GoogleMapsAPIController googleApi, RoadApiCacheManager roadCache) {
        this.googleApi = googleApi;
        this.roadCache = roadCache;

        this.world = new MapObject();
        this.car = new CarObject("Car_01", world);
        this.roadPipeline = new RoadPipelineController(2, 3.7f);
    }
    public TechnicalSubsystemController(GoogleMapsAPIController googleApi) {
        this(googleApi, null);
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
    }

    public void extendRouteFromApi(APIResponseDomain response) {
        roadPipeline.appendFromApiResponse(response);
        activeRouteSegments = roadPipeline.getPhysicsSegments();
        car.setRouteSegments(activeRouteSegments);

        // success -> cooldown
        roadRequestInFlight = false;
        roadRequestCooldown = REQUEST_COOLDOWN_S;
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

        // Call external API
        APIResponseDomain more = (roadCache != null)
                ? roadCache.getStreet(lat, lon, headDeg)
                : googleApi.getStreet(lat, lon, headDeg);

        // Append into our road pipeline
        extendRouteFromApi(more);
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

    public double getCurrentLatitude() {
        PhysicsRoadSegment seg = car.getCurrentSegment();
        if (seg == null || seg.getOriginalSegment() == null) return Double.NaN;
        return seg.getOriginalSegment().getLatitude();
    }

    public double getCurrentLongitude() {
        PhysicsRoadSegment seg = car.getCurrentSegment();
        if (seg == null || seg.getOriginalSegment() == null) return Double.NaN;
        return seg.getOriginalSegment().getLongitude();
    }
}
