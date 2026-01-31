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

    /**
     * Choose an origin (usually first point of the polyline) so coordinates stay small.
     */
    public RoadSegmentConverter(double originLat, double originLon, int defaultLaneCount, float defaultLaneWidthMeters) {
        this.originLat = originLat;
        this.originLon = originLon;
        this.defaultLaneCount = defaultLaneCount;
        this.defaultLaneWidthMeters = defaultLaneWidthMeters;
    }

    /**
     * Convert a polyline of geo points into physics/engine road segments.
     * Each consecutive pair becomes one PhysicsRoadSegment.
     */
    public List<PhysicsRoadSegment> toPhysicsSegments(List<RoadSegment> geoPoints) {
        List<PhysicsRoadSegment> out = new ArrayList<>();
        if (geoPoints == null || geoPoints.size() < 2) return out;

        for (int i = 0; i < geoPoints.size() - 1; i++) {
            RoadSegment a = geoPoints.get(i);
            RoadSegment b = geoPoints.get(i + 1);

            Vector3f start = toLocalMetres(a.getLatitude(), a.getLongitude());
            Vector3f end   = toLocalMetres(b.getLatitude(), b.getLongitude());

            // Skip degenerate tiny segments (duplicate points)
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
        double metresEast  = dLon * METRES_PER_DEG_LAT * Math.cos(Math.toRadians(originLat));

        return new Vector3f((float) metresEast, 0f, (float) metresNorth);
    }

    /**
     * Convenience: pick origin from the first point automatically.
     */
    public static RoadSegmentConverter fromFirstPoint(List<RoadSegment> geoPoints, int laneCount, float laneWidthMeters) {
        if (geoPoints == null || geoPoints.isEmpty()) {
            // Fallback origin; avoid crashing. You can choose 0,0 or Ottawa centre etc.
            return new RoadSegmentConverter(0, 0, laneCount, laneWidthMeters);
        }
        RoadSegment first = geoPoints.get(0);
        return new RoadSegmentConverter(first.getLatitude(), first.getLongitude(), laneCount, laneWidthMeters);
    }
}
