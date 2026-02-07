package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.Objects;

public class PhysicsRoadSegment {

    private static final float QUANT_METRES = 0.02f; // 2 cm grid to kill float jitter

    private final Vector3f startPoint;
    private final Vector3f endPoint;
    private final int laneCount;
    private final float laneWidthMeters;

    public PhysicsRoadSegment(Vector3f startPoint, Vector3f endPoint, int laneCount, float laneWidthMeters) {
        if (startPoint == null) throw new IllegalArgumentException("startPoint cannot be null");
        if (endPoint == null) throw new IllegalArgumentException("endPoint cannot be null");
        if (laneCount <= 0) throw new IllegalArgumentException("laneCount must be > 0");
        if (laneWidthMeters <= 0f) throw new IllegalArgumentException("laneWidthMeters must be > 0");

        // Defensive copies (Vector3f is mutable)
        this.startPoint = startPoint.clone();
        this.endPoint = endPoint.clone();
        this.laneCount = laneCount;
        this.laneWidthMeters = laneWidthMeters;
    }

    public Vector3f getStartPoint() { return startPoint.clone(); }
    public Vector3f getEndPoint()   { return endPoint.clone(); }
    public int getLaneCount()       { return laneCount; }
    public float getLaneWidth()     { return laneWidthMeters; }

    public float getLength() {
        return endPoint.distance(startPoint);
    }

    // --------- NEW: value identity ---------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PhysicsRoadSegment other)) return false;

        Key a = this.key();
        Key b = other.key();
        return a.equals(b);
    }

    @Override
    public int hashCode() {
        return key().hashCode();
    }

    // Direction-normalize + quantize for stable equality
    private Key key() {
        QuantPoint p1 = quant(startPoint);
        QuantPoint p2 = quant(endPoint);

        // Normalize direction so segment AB == BA
        if (p1.compareTo(p2) <= 0) {
            return new Key(p1, p2, laneCount, quantLaneWidth(laneWidthMeters));
        } else {
            return new Key(p2, p1, laneCount, quantLaneWidth(laneWidthMeters));
        }
    }

    private static QuantPoint quant(Vector3f v) {
        return new QuantPoint(q(v.x), q(v.y), q(v.z));
    }

    private static int q(float metres) {
        // e.g. 2cm grid: 1.234 -> 62
        return Math.round(metres / QUANT_METRES);
    }

    private static int quantLaneWidth(float w) {
        // lane width doesn't need super precision; quantize similarly
        return Math.round(w / QUANT_METRES);
    }

    private record Key(QuantPoint a, QuantPoint b, int laneCount, int laneWidthQ) {}

    private record QuantPoint(int x, int y, int z) implements Comparable<QuantPoint> {
        @Override
        public int compareTo(QuantPoint o) {
            if (x != o.x) return Integer.compare(x, o.x);
            if (z != o.z) return Integer.compare(z, o.z);
            return Integer.compare(y, o.y);
        }
    }

    @Override
    public String toString() {
        return "PhysicsRoadSegment{" +
                "start=" + startPoint +
                ", end=" + endPoint +
                ", lanes=" + laneCount +
                ", laneWidth=" + laneWidthMeters +
                '}';
    }
}
