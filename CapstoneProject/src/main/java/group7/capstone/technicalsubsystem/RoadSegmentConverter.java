package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

public class RoadSegmentConverter {

    // Roughly correct for city-scale (Ottawa size) maps.
    private static final double METRES_PER_DEG_LAT = 111_320.0;

    private final double originLat;
    private final double originLon;

    private final int defaultLaneCount;
    private final float defaultLaneWidthMeters;

    // NEW: coarser sampling so we don't create a physics segment per polyline point
    // Tune this: 5f (smoother, more bodies) .. 20f (coarser, fewer bodies)
    private final float sampleEveryMeters;

    /**
     * Choose an origin (usually first point of the polyline) so coordinates stay small.
     */
    public RoadSegmentConverter(double originLat, double originLon,
                                int defaultLaneCount, float defaultLaneWidthMeters) {
        this(originLat, originLon, defaultLaneCount, defaultLaneWidthMeters, 10f);
    }

    /**
     * Overload: allow custom sampling distance.
     */
    public RoadSegmentConverter(double originLat, double originLon,
                                int defaultLaneCount, float defaultLaneWidthMeters,
                                float sampleEveryMeters) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.defaultLaneCount = defaultLaneCount;
        this.defaultLaneWidthMeters = defaultLaneWidthMeters;
        this.sampleEveryMeters = sampleEveryMeters <= 0f ? 10f : sampleEveryMeters;
    }

    /**
     * Convert a polyline of geo points into physics/engine road segments.
     *
     * IMPORTANT:
     * - We DO NOT make a segment for every point-pair anymore.
     * - We keep a point only every N metres (sampleEveryMeters), then build segments from those.
     */
    public List<PhysicsRoadSegment> toPhysicsSegments(List<RoadSegment> geoPoints) {
        List<PhysicsRoadSegment> out = new ArrayList<>();
        if (geoPoints == null || geoPoints.size() < 2) return out;

        // 1) Convert + keep only points every N metres
        List<Vector3f> kept = new ArrayList<>();

        Vector3f lastKept = toLocalMetres(
                geoPoints.get(0).getLatitude(),
                geoPoints.get(0).getLongitude()
        );
        kept.add(lastKept);

        for (int i = 1; i < geoPoints.size(); i++) {
            RoadSegment p = geoPoints.get(i);
            Vector3f cur = toLocalMetres(p.getLatitude(), p.getLongitude());

            // Skip degenerate tiny moves (duplicate / extremely close points)
            if (cur.distance(lastKept) < 0.05f) {
                continue;
            }

            // Keep this point only if we've moved enough metres from the last kept point
            if (cur.distance(lastKept) >= sampleEveryMeters) {
                kept.add(cur);
                lastKept = cur;
            }
        }

        // Always include final point so the road reaches the end
        Vector3f finalPt = toLocalMetres(
                geoPoints.get(geoPoints.size() - 1).getLatitude(),
                geoPoints.get(geoPoints.size() - 1).getLongitude()
        );
        if (finalPt.distance(kept.get(kept.size() - 1)) > 0.05f) {
            kept.add(finalPt);
        }

        // 2) Build PhysicsRoadSegments from the kept points
        for (int i = 0; i < kept.size() - 1; i++) {
            Vector3f start = kept.get(i);
            Vector3f end = kept.get(i + 1);

            if (start.distance(end) < 0.05f) continue;

            out.add(new PhysicsRoadSegment(start, end, defaultLaneCount, defaultLaneWidthMeters));
        }

        return out;
    }

    /**
     * Local tangent-plane approximation:
     * X = east (metres), Z = north (metres), Y = up.
     */
    private Vector3f toLocalMetres(double lat, double lon) {
        double dLat = lat - originLat;
        double dLon = lon - originLon;

        double metresNorth = dLat * METRES_PER_DEG_LAT;
        double metresEast = dLon * METRES_PER_DEG_LAT * Math.cos(Math.toRadians(originLat));

        return new Vector3f((float) metresEast, 0f, (float) metresNorth);
    }

    /**
     * Convenience: pick origin from the first point automatically.
     */
    public static RoadSegmentConverter fromFirstPoint(List<RoadSegment> geoPoints,
                                                      int laneCount,
                                                      float laneWidthMeters) {
        if (geoPoints == null || geoPoints.isEmpty()) {
            // Fallback origin; avoid crashing. You can choose 0,0 or Ottawa centre etc.
            return new RoadSegmentConverter(0, 0, laneCount, laneWidthMeters);
        }
        RoadSegment first = geoPoints.get(0);
        return new RoadSegmentConverter(first.getLatitude(), first.getLongitude(), laneCount, laneWidthMeters);
    }

    /**
     * Convenience overload: pick origin from first point AND configure sampling distance.
     */
    public static RoadSegmentConverter fromFirstPoint(List<RoadSegment> geoPoints,
                                                      int laneCount,
                                                      float laneWidthMeters,
                                                      float sampleEveryMeters) {
        if (geoPoints == null || geoPoints.isEmpty()) {
            return new RoadSegmentConverter(0, 0, laneCount, laneWidthMeters, sampleEveryMeters);
        }
        RoadSegment first = geoPoints.get(0);
        return new RoadSegmentConverter(
                first.getLatitude(),
                first.getLongitude(),
                laneCount,
                laneWidthMeters,
                sampleEveryMeters
        );
    }
}
