package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.List;

/**
 * SoftRailFollower (teleport-only)
 * - Detects whether the car is inside ANY road corridor.
 * - If not, returns nearest snap point + segment direction.
 * - Tracks current segment index + progress t (0..1) when on-road.
 *
 * No steering. No forces. No "next segment" logic.
 */
public class SoftRailFollower {

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
        public final float distanceMeters;   // lateral distance to corridor centreline

        public final int segmentIndex;       // -1 if offRoad
        public final float tOnSegment;       // 0..1 if onRoad

        Result(boolean offRoad,
               Vector3f snapPoint,
               Vector3f forwardXZ,
               float distanceMeters,
               int segmentIndex,
               float tOnSegment) {
            this.offRoad = offRoad;
            this.snapPoint = snapPoint;
            this.forwardXZ = forwardXZ;
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
            return new Result(false, null, null, 0f, -1, 0f);
        }

        Vector3f pos = physics.getPosition();

        float bestDist2 = Float.POSITIVE_INFINITY;
        Vector3f bestPoint = null;
        Vector3f bestForward = null;

        for (int i = 0; i < segments.size(); i++) {
            PhysicsRoadSegment seg = segments.get(i);

            float halfWidth = (seg.getLaneCount() * seg.getLaneWidth()) * 0.5f;

            ClosestPoint cp = closestPointOnSegmentXZ_WithT(
                    pos,
                    seg.getStartPoint(),
                    seg.getEndPoint()
            );

            float dist = (float) Math.sqrt(cp.dist2);

            // Inside corridor => ON ROAD
            if (dist <= halfWidth) {
                currentSegment = seg;
                currentIndex = i;
                currentT = cp.t;
                return new Result(false, null, null, dist, i, cp.t);
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
                (float) Math.sqrt(bestDist2),
                -1,
                0f
        );
    }

    // ------------------------------------------------------------------

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
