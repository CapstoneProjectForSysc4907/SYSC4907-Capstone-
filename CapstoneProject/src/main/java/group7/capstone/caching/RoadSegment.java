package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This is a stub that will be replaced when Action 3 API Controller is done.
 *
 * Represents a snapped road as a sequence of geographic points.
 */
public class RoadSegment {

    private static final double DEFAULT_SPEED_LIMIT = 60.0;

    private final List<Point> points;
    private final double speedLimit;
    private final String roadName;

    /**
     * Constructs a RoadSegment from an APIResponseDomain.
     * Only latitude/longitude are extracted.
     */
    public RoadSegment(APIResponseDomain response) {
        this.speedLimit = DEFAULT_SPEED_LIMIT;
        this.roadName = null;
        this.points = extractPoints(response);
    }

    /**
     * Returns an immutable list of points making up this road segment.
     */
    public List<Point> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public double getSpeedLimit() {
        return speedLimit;
    }

    public String getRoadName() {
        return roadName;
    }

    private List<Point> extractPoints(APIResponseDomain response) {
        List<Point> result = new ArrayList<>();

        if (response == null || response.getSnappedPoints() == null) {
            return result;
        }

        for (APIResponseDomain.SnappedPoint snapped : response.getSnappedPoints()) {
            APIResponseDomain.LatLng loc = snapped.getLocation();
            if (loc == null) continue;

            result.add(new Point(loc.getLatitude(), loc.getLongitude()));
        }

        return result;
    }

    @Override
    public String toString() {
        return "RoadSegment{points=" + points.size()
                + ", speedLimit=" + speedLimit + " km/h}";
    }

    /* ============================================================
       INTERNAL POINT TYPE — NOT EXPOSED OUTSIDE RoadSegment
       ============================================================ */

    public static final class Point {
        private final double latitude;
        private final double longitude;

        private Point(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        @Override
        public String toString() {
            return String.format("(%.6f, %.6f)", latitude, longitude);
        }
    }
}
