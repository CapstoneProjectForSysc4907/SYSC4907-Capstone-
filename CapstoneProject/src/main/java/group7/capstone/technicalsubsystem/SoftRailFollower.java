package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.List;

/**
 * SoftRailFollower (teleport-only)
 * - Detects whether the car is inside ANY road corridor.
 * - If not, returns nearest snap point + segment direction.
 * - Tracks current segment index + progress t (0..1) when on-road.
 *
 */
public class SoftRailFollower {

    private static final float CORNER_GRACE_RADIUS = 9.0f;
    private static final float CORNER_GRACE_RADIUS_SQ = CORNER_GRACE_RADIUS * CORNER_GRACE_RADIUS;

    private PhysicsRoadSegment currentSegment = null;
    private int currentIndex = -1;
    private float currentT = 0f; // 0..1 along segment

    public PhysicsRoadSegment getCurrentSegment() { return currentSegment; }
    public int getCurrentIndex() { return currentIndex; }
    public float getCurrentT() { return currentT; }

    public static class Result {
        public final boolean offRoad;
        public final Vector3f snapPoint;     // teleport target (XZ snapped, Y preserved)
        public final Vector3f forwardXZ;     // segment direction (unit XZ)
        public final Vector3f rightXZ;       // perpendicular to segment direction (unit XZ)
        public final float distanceMeters;   // lateral distance to corridor centreline

        public final int segmentIndex;       // -1 if offRoad
        public final float tOnSegment;       // 0..1 if onRoad

        Result(boolean offRoad,
               Vector3f snapPoint,
               Vector3f forwardXZ,
               Vector3f rightXZ,
               float distanceMeters,
               int segmentIndex,
               float tOnSegment) {
            this.offRoad = offRoad;
            this.snapPoint = snapPoint;
            this.forwardXZ = forwardXZ;
            this.rightXZ = rightXZ;
            this.distanceMeters = distanceMeters;
            this.segmentIndex = segmentIndex;
            this.tOnSegment = tOnSegment;
        }
    }

    public Result check(VehiclePhysicsSystem physics, List<PhysicsRoadSegment> segments) {

        if (physics == null || segments == null || segments.isEmpty()) {
            currentSegment = null;
            currentIndex = -1;
            currentT = 0f;
            return new Result(false, null, null, null, 0f, -1, 0f);
        }

        Vector3f pos = physics.getPosition();

        float speedKmh = physics.getSpeedKmh();
        float speedPad = clamp(1.25f + (speedKmh / 100f) * 1.5f, 1.25f, 3.5f);

        float bestDist2 = Float.POSITIVE_INFINITY;
        Vector3f bestPoint = null;
        Vector3f bestForward = null;
        Vector3f bestRight = null;

        if (currentIndex >= 0 && currentIndex < segments.size()) {
            PhysicsRoadSegment seg = segments.get(currentIndex);
            float innerHalfWidth = computeInnerHalfWidth(seg);
            float outerHalfWidth = innerHalfWidth + speedPad;

            ClosestPoint cp = closestPointOnSegmentXZ_WithT(pos, seg.getStartPoint(), seg.getEndPoint());
            float dist = (float) Math.sqrt(cp.dist2);

            boolean nearCorner =
                    pointDist2XZ(pos, seg.getStartPoint()) <= CORNER_GRACE_RADIUS_SQ ||
                            pointDist2XZ(pos, seg.getEndPoint()) <= CORNER_GRACE_RADIUS_SQ;

            if (dist <= outerHalfWidth || nearCorner) {
                currentSegment = seg;
                currentT = cp.t;
                return new Result(false, null, null, null, dist, currentIndex, cp.t);
            }
        }

        // Full scan
        for (int i = 0; i < segments.size(); i++) {
            PhysicsRoadSegment seg = segments.get(i);

            float innerHalfWidth = computeInnerHalfWidth(seg);

            ClosestPoint cp = closestPointOnSegmentXZ_WithT(
                    pos,
                    seg.getStartPoint(),
                    seg.getEndPoint()
            );

            float dist = (float) Math.sqrt(cp.dist2);

            boolean nearCorner =
                    pointDist2XZ(pos, seg.getStartPoint()) <= CORNER_GRACE_RADIUS_SQ ||
                            pointDist2XZ(pos, seg.getEndPoint()) <= CORNER_GRACE_RADIUS_SQ;

            // Inside corridor or near joined endpoint
            if (dist <= innerHalfWidth || nearCorner) {
                currentSegment = seg;
                currentIndex = i;
                currentT = cp.t;
                return new Result(false, null, null, null, dist, i, cp.t);
            }

            // Track nearest for teleport
            if (cp.dist2 < bestDist2) {
                bestDist2 = cp.dist2;

                bestPoint = new Vector3f(cp.point.x, pos.y, cp.point.z);

                Vector3f f = seg.getEndPoint().subtract(seg.getStartPoint());
                f.y = 0f;
                if (f.lengthSquared() < 1e-6f) f.set(0, 0, 1);
                f.normalizeLocal();
                bestForward = f;

                Vector3f r = new Vector3f(-f.z, 0f, f.x);
                if (r.lengthSquared() < 1e-6f) r.set(1, 0, 0);
                r.normalizeLocal();
                bestRight = r;
            }
        }

        // Off-road
        currentSegment = null;
        currentIndex = -1;
        currentT = 0f;

        return new Result(
                true,
                bestPoint,
                bestForward,
                bestRight,
                (float) Math.sqrt(bestDist2),
                -1,
                0f
        );
    }

    private static float computeInnerHalfWidth(PhysicsRoadSegment seg) {
        float base = (seg.getLaneCount() * seg.getLaneWidth()) * 0.5f;

        float minHalfWidth = 4.0f;
        float fixedPad = 1.0f;

        return Math.max(base, minHalfWidth) + fixedPad;
    }

    private static float pointDist2XZ(Vector3f a, Vector3f b) {
        float dx = a.x - b.x;
        float dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static class ClosestPoint {
        final Vector3f point;
        final float dist2;
        final float t;

        ClosestPoint(Vector3f point, float dist2, float t) {
            this.point = point;
            this.dist2 = dist2;
            this.t = t;
        }
    }

    private static ClosestPoint closestPointOnSegmentXZ_WithT(Vector3f p, Vector3f a, Vector3f b) {

        float ax = a.x, az = a.z;
        float bx = b.x, bz = b.z;
        float px = p.x, pz = p.z;

        float abx = bx - ax;
        float abz = bz - az;

        float apx = px - ax;
        float apz = pz - az;

        float abLen2 = abx * abx + abz * abz;
        if (abLen2 < 1e-6f) {
            float dx = px - ax;
            float dz = pz - az;
            return new ClosestPoint(new Vector3f(ax, 0, az), dx * dx + dz * dz, 0f);
        }

        float t = (apx * abx + apz * abz) / abLen2;
        t = Math.max(0f, Math.min(1f, t));

        float cx = ax + abx * t;
        float cz = az + abz * t;

        float dx = px - cx;
        float dz = pz - cz;

        return new ClosestPoint(new Vector3f(cx, 0, cz), dx * dx + dz * dz, t);
    }
}