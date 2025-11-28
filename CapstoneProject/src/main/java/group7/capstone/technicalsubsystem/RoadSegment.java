package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

public class RoadSegment {

    private final Vector3f startPoint;
    private final Vector3f endPoint;

    // Hard-coded for now
    private final int laneCount = 2;
    private final float laneWidth = 3f; // 2 lanes × 3m each = 6m wide
    private final float speedLimit = 20f; // m/s (about 72 km/h)

    public RoadSegment(Vector3f startPoint, Vector3f endPoint) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
    }

    public float getLength() {
        return startPoint.distance(endPoint);
    }

    public int getLaneCount() {
        return laneCount;
    }

    public float getLaneWidth() {
        return laneWidth;
    }

    public Vector3f getStartPoint() {
        return startPoint;
    }

    public Vector3f getEndPoint() {
        return endPoint;
    }
}
