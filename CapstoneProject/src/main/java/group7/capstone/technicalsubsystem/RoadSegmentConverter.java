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

    // Coarser sampling so we don't create a physics segment per polyline point
    // Tune this: 5f (smoother, more bodies) .. 20f (coarser, fewer bodies)
    private final float sampleEveryMeters;

    // ===== Debug toggles =====
    private static final boolean DEBUG = true;
    private static final int DEBUG_MAX_PRINT = 5;

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
        this.sampleEveryMeters = (sampleEveryMeters <= 0f) ? 10f : sampleEveryMeters;
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

        // Find first non-null point as p0
        int firstIdx = -1;
        for (int i = 0; i < geoPoints.size(); i++) {
            if (geoPoints.get(i) != null) {
                firstIdx = i;
                break;
            }
        }
        if (firstIdx < 0 || firstIdx == geoPoints.size() - 1) return out;

        RoadSegment p0 = geoPoints.get(firstIdx);

        // Find next non-null point as p1 (for debug)
        RoadSegment p1 = null;
        for (int i = firstIdx + 1; i < geoPoints.size(); i++) {
            if (geoPoints.get(i) != null) {
                p1 = geoPoints.get(i);
                break;
            }
        }

        // ===== DEBUG: verify geo input + lon/lat ranges + first-step conversion =====
        if (DEBUG) {
            System.out.println("[RoadSegmentConverter] originLat=" + originLat + " originLon=" + originLon);
            System.out.println("[RoadSegmentConverter] sampleEveryMeters=" + sampleEveryMeters);
            System.out.println("[RoadSegmentConverter] p0=" + p0 + " p1=" + p1);

            if (p1 != null) {
                double dLat01 = p1.getLatitude() - p0.getLatitude();
                double dLon01 = p1.getLongitude() - p0.getLongitude();
                System.out.println("[RoadSegmentConverter] dLat01=" + dLat01 + " dLon01=" + dLon01);

                Vector3f local0 = toLocalMetres(p0.getLatitude(), p0.getLongitude());
                Vector3f local1 = toLocalMetres(p1.getLatitude(), p1.getLongitude());
                System.out.println("[RoadSegmentConverter] local0=" + local0 + " local1=" + local1
                        + " deltaLocal=" + local1.subtract(local0));
            }

            double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
            double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;
            int nonNullCount = 0;

            for (RoadSegment p : geoPoints) {
                if (p == null) continue;
                nonNullCount++;
                minLat = Math.min(minLat, p.getLatitude());
                maxLat = Math.max(maxLat, p.getLatitude());
                minLon = Math.min(minLon, p.getLongitude());
                maxLon = Math.max(maxLon, p.getLongitude());
            }

            System.out.println("[RoadSegmentConverter] latRange=" + (maxLat - minLat)
                    + " lonRange=" + (maxLon - minLon)
                    + " points=" + geoPoints.size()
                    + " nonNull=" + nonNullCount);

            int printed = 0;
            for (int i = 0; i < geoPoints.size() && printed < DEBUG_MAX_PRINT; i++) {
                RoadSegment p = geoPoints.get(i);
                if (p == null) continue;
                System.out.println("[RoadSegmentConverter] raw[" + i + "]=" + p);
                printed++;
            }
        }

        // 1) Convert + keep only points every N metres
        List<Vector3f> kept = new ArrayList<>();

        Vector3f lastKept = toLocalMetres(p0.getLatitude(), p0.getLongitude());
        kept.add(lastKept);

        for (int i = firstIdx + 1; i < geoPoints.size(); i++) {
            RoadSegment p = geoPoints.get(i);
            if (p == null) continue;

            Vector3f cur = toLocalMetres(p.getLatitude(), p.getLongitude());
            float dist = cur.distance(lastKept);

            // Skip degenerate tiny moves (duplicate / extremely close points)
            if (dist < 0.05f) continue;

            // Keep this point only if we've moved enough metres from the last kept point
            if (dist >= sampleEveryMeters) {
                kept.add(cur);
                lastKept = cur;
            }
        }

        // Always include final non-null point so the road reaches the end
        RoadSegment lastNonNull = null;
        for (int i = geoPoints.size() - 1; i >= 0; i--) {
            if (geoPoints.get(i) != null) {
                lastNonNull = geoPoints.get(i);
                break;
            }
        }

        if (lastNonNull != null) {
            Vector3f finalPt = toLocalMetres(lastNonNull.getLatitude(), lastNonNull.getLongitude());
            if (finalPt.distance(kept.get(kept.size() - 1)) > 0.05f) {
                kept.add(finalPt);
            }
        }

        // 2) Build PhysicsRoadSegments from the kept points
        for (int i = 0; i < kept.size() - 1; i++) {
            Vector3f start = kept.get(i);
            Vector3f end = kept.get(i + 1);

            if (start.distance(end) < 0.05f) continue;

            // Clone so no one can mutate internal segment endpoints later
            out.add(new PhysicsRoadSegment(start.clone(), end.clone(), defaultLaneCount, defaultLaneWidthMeters));
        }

        if (DEBUG) {
            System.out.println("[RoadSegmentConverter] keptPoints=" + kept.size() + " physicsSegs=" + out.size());
            if (!out.isEmpty()) {
                PhysicsRoadSegment s0 = out.get(0);
                float len0 = s0.getEndPoint().subtract(s0.getStartPoint()).length();
                System.out.println("[RoadSegmentConverter] seg0=" + s0.getStartPoint() + " -> " + s0.getEndPoint()
                        + " len=" + len0);
            }
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
            return new RoadSegmentConverter(0, 0, laneCount, laneWidthMeters);
        }

        RoadSegment firstNonNull = null;
        for (RoadSegment p : geoPoints) {
            if (p != null) {
                firstNonNull = p;
                break;
            }
        }

        if (firstNonNull == null) {
            return new RoadSegmentConverter(0, 0, laneCount, laneWidthMeters);
        }

        return new RoadSegmentConverter(firstNonNull.getLatitude(), firstNonNull.getLongitude(), laneCount, laneWidthMeters);
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

        RoadSegment firstNonNull = null;
        for (RoadSegment p : geoPoints) {
            if (p != null) {
                firstNonNull = p;
                break;
            }
        }

        if (firstNonNull == null) {
            return new RoadSegmentConverter(0, 0, laneCount, laneWidthMeters, sampleEveryMeters);
        }

        return new RoadSegmentConverter(
                firstNonNull.getLatitude(),
                firstNonNull.getLongitude(),
                laneCount,
                laneWidthMeters,
                sampleEveryMeters
        );
    }
}
