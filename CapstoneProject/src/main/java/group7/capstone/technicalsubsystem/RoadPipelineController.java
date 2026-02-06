package group7.capstone.technicalsubsystem;

import group7.capstone.APIController.APIResponseDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Runs the pipeline end-to-end:
 * APIResponseDomain -> caching.RoadSegment (polyline)
 * -> technicalsubsystem.RoadSegment (geo points stored in RoadDataHolder)
 * -> PhysicsRoadSegment (engine-ready segments)
 *
 * Holds both:
 * - geoPoints (technicalsubsystem.RoadSegment)
 * - physicsSegments (technicalsubsystem.PhysicsRoadSegment)
 *
 * NOTE: There are TWO classes named RoadSegment in your codebase.
 * This controller treats:
 *  - group7.capstone.caching.RoadSegment as "snapped polyline"
 *  - group7.capstone.technicalsubsystem.RoadSegment as "geo point"
 */
public final class RoadPipelineController {

    private final RoadDataHolder roadDataHolder;

    // Latest pipeline outputs
    private final List<RoadSegment> geoPoints = new ArrayList<>();
    private final List<PhysicsRoadSegment> physicsSegments = new ArrayList<>();

    // Config
    private int defaultLaneCount;
    private float defaultLaneWidthMeters;

    public RoadPipelineController(int defaultLaneCount, float defaultLaneWidthMeters) {
        this.roadDataHolder = RoadDataHolder.getInstance();
        this.defaultLaneCount = defaultLaneCount;
        this.defaultLaneWidthMeters = defaultLaneWidthMeters;
    }

    /**
     * Run the pipeline starting from the API response.
     * This will:
     * 1) create caching.RoadSegment (polyline)
     * 2) adapt to technicalsubsystem geo points
     * 3) store geo points in RoadDataHolder
     * 4) convert to PhysicsRoadSegments
     */
    public synchronized void runFromApiResponse(APIResponseDomain response) {
        Objects.requireNonNull(response, "API response cannot be null");
        group7.capstone.caching.RoadSegment snapped = new group7.capstone.caching.RoadSegment(response);
        runFromSnappedRoad(snapped);
    }

    /**
     * Run the pipeline starting from the snapped road polyline (caching layer).
     */
    public synchronized void runFromSnappedRoad(group7.capstone.caching.RoadSegment snappedRoad) {
        Objects.requireNonNull(snappedRoad, "snappedRoad cannot be null");

        // Reset previous outputs
        clearLocalOutputs();
        // Clear holder (optional; keep if you want RoadDataHolder to always represent "current" road)
        roadDataHolder.emptyRoad();

        // 1) caching polyline -> 2) technical geo points
        List<RoadSegment> convertedGeo = adaptSnappedToGeoPoints(snappedRoad);

        // 3) store in RoadDataHolder + local cache
        for (RoadSegment p : convertedGeo) {
            roadDataHolder.addRoadData(p);
            geoPoints.add(p);
        }

        // 4) convert geo points -> physics segments
        RoadSegmentConverter converter =
                RoadSegmentConverter.fromFirstPoint(geoPoints, defaultLaneCount, defaultLaneWidthMeters);

        physicsSegments.addAll(converter.toPhysicsSegments(geoPoints));
    }

    /**
     * If you already have geo points in RoadDataHolder (or elsewhere),
     * you can run conversion directly.
     */
    public synchronized void runFromGeoPoints(List<RoadSegment> geoPointsInput) {
        clearLocalOutputs();
        roadDataHolder.emptyRoad();

        if (geoPointsInput == null || geoPointsInput.size() < 2) {
            return;
        }

        for (RoadSegment p : geoPointsInput) {
            if (p == null) continue;
            roadDataHolder.addRoadData(p);
            geoPoints.add(p);
        }

        RoadSegmentConverter converter =
                RoadSegmentConverter.fromFirstPoint(geoPoints, defaultLaneCount, defaultLaneWidthMeters);

        physicsSegments.addAll(converter.toPhysicsSegments(geoPoints));
    }

    public synchronized List<RoadSegment> getGeoPoints() {
        return Collections.unmodifiableList(new ArrayList<>(geoPoints));
    }

    public synchronized List<PhysicsRoadSegment> getPhysicsSegments() {
        return Collections.unmodifiableList(new ArrayList<>(physicsSegments));
    }

    public synchronized List<RoadSegment> getGeoPointsFromHolder() {
        // NOTE: returns the holder’s internal list reference in your current RoadDataHolder design.
        // We'll wrap it so callers can't mutate it.
        return Collections.unmodifiableList(new ArrayList<>(roadDataHolder.getRoadList()));
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

    /* -------------------- internals -------------------- */

    private void clearLocalOutputs() {
        geoPoints.clear();
        physicsSegments.clear();
    }

    private List<RoadSegment> adaptSnappedToGeoPoints(group7.capstone.caching.RoadSegment snappedRoad) {
        List<RoadSegment> out = new ArrayList<>();
        for (group7.capstone.caching.RoadSegment.Point p : snappedRoad.getPoints()) {
            // technicalsubsystem.RoadSegment is a geo point (lat/lon)
            out.add(new RoadSegment(p.getLatitude(), p.getLongitude()));
        }
        return out;
    }
}
