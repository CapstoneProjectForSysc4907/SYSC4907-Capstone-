package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

public class PhysicsRoadSegment {

    private final Vector3f startPoint;
    private final Vector3f endPoint;
    private final int laneCount;
    private final float laneWidthMeters;

    public PhysicsRoadSegment(Vector3f startPoint, Vector3f endPoint, int laneCount, float laneWidthMeters) {
        this.startPoint = startPoint;
        this.endPoint = endPoint;
        this.laneCount = laneCount;
        this.laneWidthMeters = laneWidthMeters;
    }

    public Vector3f getStartPoint() {
        return startPoint;
    }

    public Vector3f getEndPoint() {
        return endPoint;
    }

    public int getLaneCount() {
        return laneCount;
    }

    public float getLaneWidth() {
        return laneWidthMeters;
    }

    public float getLength() {
        return endPoint.subtract(startPoint).length();
    }
}
