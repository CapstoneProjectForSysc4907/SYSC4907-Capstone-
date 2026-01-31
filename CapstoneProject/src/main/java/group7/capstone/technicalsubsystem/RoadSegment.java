package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

public class RoadSegment {

    private final double latitude;
    private final double longitude;

    private RoadSegment(double latitude, double longitude) {
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
