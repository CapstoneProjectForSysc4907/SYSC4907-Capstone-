package group7.capstone.APIController;

import java.util.List;

/**
 * Domain model for Google Roads Snap-to-Roads response.
 *
 * Structure:
 * - APIResponseDomain contains a list of SnappedPoint
 * - SnappedPoint contains a LatLng location
 * - LatLng contains latitude and longitude
 */
public class APIResponseDomain {

    private List<SnappedPoint> snappedPoints;

    public List<SnappedPoint> getSnappedPoints() {
        return snappedPoints;
    }

    public void setSnappedPoints(List<SnappedPoint> snappedPoints) {
        this.snappedPoints = snappedPoints;
    }

    /** One entry in snappedPoints[] */
    public static class SnappedPoint {

        private LatLng location;
        private Integer originalIndex; // optional
        private String placeId;

        public LatLng getLocation() {
            return location;
        }

        public void setLocation(LatLng location) {
            this.location = location;
        }

        public Integer getOriginalIndex() {
            return originalIndex;
        }

        public void setOriginalIndex(Integer originalIndex) {
            this.originalIndex = originalIndex;
        }

        public String getPlaceId() {
            return placeId;
        }

        public void setPlaceId(String placeId) {
            this.placeId = placeId;
        }
    }

    /** location object holding lat/lng */
    public static class LatLng {

        private double latitude;
        private double longitude;

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }
    }
}
