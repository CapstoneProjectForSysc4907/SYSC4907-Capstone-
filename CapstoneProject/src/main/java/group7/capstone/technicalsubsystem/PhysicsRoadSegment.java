package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

public class PhysicsRoadSegment {

    private final Vector3f startPoint;
    private final Vector3f endPoint;
    private final int laneCount;
    private final float laneWidthMeters;

    // MIN ADD: reference back to source geo point
    private final RoadSegment originalSegment;

    public PhysicsRoadSegment(Vector3f startPoint,
                              Vector3f endPoint,
                              int laneCount,
                              float laneWidthMeters,
                              RoadSegment originalSegment) {
        if (startPoint == null) throw new IllegalArgumentException("startPoint cannot be null");
        if (endPoint == null) throw new IllegalArgumentException("endPoint cannot be null");
        if (laneCount <= 0) throw new IllegalArgumentException("laneCount must be > 0");
        if (laneWidthMeters <= 0f) throw new IllegalArgumentException("laneWidthMeters must be > 0");

        // Defensive copies (Vector3f is mutable)
        this.startPoint = startPoint.clone();
        this.endPoint = endPoint.clone();
        this.laneCount = laneCount;
        this.laneWidthMeters = laneWidthMeters;

        // MIN ADD
        this.originalSegment = originalSegment;
    }

    public Vector3f getStartPoint() { return startPoint.clone(); }
    public Vector3f getEndPoint()   { return endPoint.clone(); }
    public int getLaneCount()       { return laneCount; }
    public float getLaneWidth()     { return laneWidthMeters; }

    public float getLength() {
        return endPoint.distance(startPoint);
    }

    // MIN ADD
    public RoadSegment getOriginalSegment() {
        return originalSegment;
    }

    @Override
    public String toString() {
        return "PhysicsRoadSegment{" +
                "start=" + startPoint +
                ", end=" + endPoint +
                ", lanes=" + laneCount +
                ", laneWidth=" + laneWidthMeters +
                ", originalSegment=" + originalSegment +
                '}';
    }
}
