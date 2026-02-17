package group7.capstone.technicalsubsystem;

import group7.capstone.APIController.APIResponseDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RoadPipelineController {

    private final RoadDataHolder roadDataHolder;

    private final List<RoadSegment> geoPoints = new ArrayList<>();
    private final List<PhysicsRoadSegment> physicsSegments = new ArrayList<>();

    private int defaultLaneCount;
    private float defaultLaneWidthMeters;

    public RoadPipelineController(int defaultLaneCount, float defaultLaneWidthMeters) {
        this.roadDataHolder = RoadDataHolder.getInstance();
        this.defaultLaneCount = defaultLaneCount;
        this.defaultLaneWidthMeters = defaultLaneWidthMeters;
    }

    public synchronized void runFromApiResponse(APIResponseDomain response) {
        Objects.requireNonNull(response, "API response cannot be null");
        group7.capstone.caching.RoadSegment snapped = new group7.capstone.caching.RoadSegment(response);
        runFromSnappedRoad(snapped);
    }

    public synchronized void runFromSnappedRoad(group7.capstone.caching.RoadSegment snappedRoad) {
        Objects.requireNonNull(snappedRoad, "snappedRoad cannot be null");

        clearLocalOutputs();
        roadDataHolder.emptyRoad();

        List<RoadSegment> convertedGeo = adaptSnappedToGeoPoints(snappedRoad);

        for (RoadSegment p : convertedGeo) {
            roadDataHolder.addRoadData(p);
            geoPoints.add(p);
        }

        rebuildPhysicsSegmentsFromAllGeo();
    }

    public synchronized void runFromGeoPoints(List<RoadSegment> geoPointsInput) {
        clearLocalOutputs();
        roadDataHolder.emptyRoad();

        if (geoPointsInput == null || geoPointsInput.size() < 2) return;

        for (RoadSegment p : geoPointsInput) {
            if (p == null) continue;
            roadDataHolder.addRoadData(p);
            geoPoints.add(p);
        }

        rebuildPhysicsSegmentsFromAllGeo();
    }

    /**
     * Append more road points (from API) to the current road.
     * Rebuilds physics segments after appending.
     */
    public synchronized void appendFromApiResponse(APIResponseDomain response) {
        Objects.requireNonNull(response, "API response cannot be null");

        group7.capstone.caching.RoadSegment snapped = new group7.capstone.caching.RoadSegment(response);
        List<RoadSegment> newGeo = adaptSnappedToGeoPoints(snapped);

        if (newGeo == null || newGeo.size() < 2) return;

        // If first new point duplicates last old point, drop it
        if (!geoPoints.isEmpty() && !newGeo.isEmpty()) {
            RoadSegment last = geoPoints.get(geoPoints.size() - 1);
            RoadSegment firstNew = newGeo.get(0);
            if (almostSameLatLon(last, firstNew)) {
                newGeo = newGeo.subList(1, newGeo.size());
            }
        }

        for (RoadSegment p : newGeo) {
            if (p == null) continue;
            roadDataHolder.addRoadData(p);
            geoPoints.add(p);
        }

        rebuildPhysicsSegmentsFromAllGeo();
    }

    private boolean almostSameLatLon(RoadSegment a, RoadSegment b) {
        double dLat = Math.abs(a.getLatitude() - b.getLatitude());
        double dLon = Math.abs(a.getLongitude() - b.getLongitude());
        return dLat < 1e-6 && dLon < 1e-6;
    }

    public synchronized List<RoadSegment> getGeoPoints() {
        return Collections.unmodifiableList(new ArrayList<>(geoPoints));
    }

    public synchronized List<PhysicsRoadSegment> getPhysicsSegments() {
        return Collections.unmodifiableList(new ArrayList<>(physicsSegments));
    }

    public synchronized void setDefaults(int laneCount, float laneWidthMeters) {
        if (laneCount <= 0) throw new IllegalArgumentException("laneCount must be > 0");
        if (laneWidthMeters <= 0) throw new IllegalArgumentException("laneWidthMeters must be > 0");
        this.defaultLaneCount = laneCount;
        this.defaultLaneWidthMeters = laneWidthMeters;
    }

    public synchronized void clearAll() {
        clearLocalOutputs();
        roadDataHolder.emptyRoad();
    }

    // ---- internals ----

    private void clearLocalOutputs() {
        geoPoints.clear();
        physicsSegments.clear();
    }

    private void rebuildPhysicsSegmentsFromAllGeo() {
        physicsSegments.clear();
        if (geoPoints.size() < 2) return;

        RoadSegmentConverter converter =
                RoadSegmentConverter.fromFirstPoint(geoPoints, defaultLaneCount, defaultLaneWidthMeters);

        physicsSegments.addAll(converter.toPhysicsSegments(geoPoints));
    }

    private List<RoadSegment> adaptSnappedToGeoPoints(group7.capstone.caching.RoadSegment snappedRoad) {
        List<RoadSegment> out = new ArrayList<>();
        for (group7.capstone.caching.RoadSegment.Point p : snappedRoad.getPoints()) {
            out.add(new RoadSegment(p.getLatitude(), p.getLongitude()));
        }
        return out;
    }
}
