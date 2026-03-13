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

    private final GoogleMapsAPIController googleApi;
    private final RoadApiCacheManager roadCache;

    private List<PhysicsRoadSegment> activeRouteSegments = Collections.emptyList();

    private float roadRequestCooldown = 0f;
    private boolean roadRequestInFlight = false;

    // FIX: was 60f. Increased to 300m so the next road segment is requested early enough
    // to arrive before the car runs out of road, especially important at intersections.
    private static final float NEED_MORE_THRESHOLD_M = 300f;
    private static final float REQUEST_COOLDOWN_S = 1.5f;

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

    public void updateAndMaybeRequestMoreRoad(float throttle, float brake, float steering, float dt) {
        car.update(throttle, brake, steering, dt);
        world.step(dt);

        roadRequestCooldown = Math.max(0f, roadRequestCooldown - dt);

        if (shouldRequestMoreRoadInternal()) {
            requestMoreRoadNow();
        }
    }

    public void update(float throttle, float brake, float steering, float dt) {
        car.update(throttle, brake, steering, dt);
        world.step(dt);
        roadRequestCooldown = Math.max(0f, roadRequestCooldown - dt);
    }

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

        roadRequestInFlight = false;
        roadRequestCooldown = REQUEST_COOLDOWN_S;
    }

    private boolean shouldRequestMoreRoadInternal() {
        if (roadRequestInFlight) return false;
        if (roadRequestCooldown > 0f) return false;

        if (activeRouteSegments == null || activeRouteSegments.isEmpty()) return false;

        if (!car.isOnRoad()) return false;
        PhysicsRoadSegment seg = car.getCurrentSegment();
        if (seg == null) return false;

        float remaining = car.getRemainingRoadMeters();
        return remaining < NEED_MORE_THRESHOLD_M;
    }

    private void requestMoreRoadNow() {
        roadRequestInFlight = true;

        double lat = car.getCurrentLatitude();
        double lon = car.getCurrentLongitude();

        if (Double.isNaN(lat) || Double.isNaN(lon)) {
            roadRequestInFlight = false;
            roadRequestCooldown = REQUEST_COOLDOWN_S;
            return;
        }

        int headDeg = car.getHeadingDegrees();

        APIResponseDomain more = (roadCache != null)
                ? roadCache.getStreet(lat, lon, headDeg)
                : googleApi.getStreet(lat, lon, headDeg);

        if (more == null) {
            roadRequestInFlight = false;
            roadRequestCooldown = REQUEST_COOLDOWN_S;
            return;
        }

        extendRouteFromApi(more);
    }

    public List<PhysicsRoadSegment> getActiveRouteSegments() {
        return activeRouteSegments;
    }

    public float getSpeedKmh() {
        return car.getSpeed();
    }

    public String getPositionString() {
        return car.getPosition().toString();
    }

    public String getOrientationString() {
        return car.getCompassDirection();
    }

    public Vector3f getPosition() {
        return car.getPosition();
    }

    public float getStopDistance() {
        return car.getStopDistance();
    }

    public boolean isOnRoad() {
        return car.isOnRoad();
    }

    public int getGeoPointCount() {
        return roadPipeline.getGeoPoints().size();
    }

    public int getPhysicsSegmentCount() {
        return roadPipeline.getPhysicsSegments().size();
    }

    public float getRemainingRoadMeters() {
        return car.getRemainingRoadMeters();
    }

    public int getHeadingDegrees() {
        return car.getHeadingDegrees();
    }

    public double getCurrentLatitude() {
        return car.getCurrentLatitude();
    }

    public double getCurrentLongitude() {
        return car.getCurrentLongitude();
    }
}