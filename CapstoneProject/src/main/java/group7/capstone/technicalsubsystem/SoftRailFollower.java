package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;
import java.util.List;

/**
 * SoftRailFollower (teleport-only)
 *
 * Uses the provided PhysicsRoadSegments as the source of truth.
 * If the car is outside ALL segment corridors, signals teleport back.
 *
 * No steering.
 * No forces.
 * No segment tracking.
 * No "next line" logic.
 */
public class SoftRailFollower {

    public static class Result {
        public final boolean offRoad;
        public final Vector3f snapPoint;     // where to teleport (XZ snapped, Y preserved)
        public final Vector3f forwardXZ;     // segment direction (optional)
        public final float distanceMeters;

        Result(boolean offRoad, Vector3f snapPoint, Vector3f forwardXZ, float distanceMeters) {
            this.offRoad = offRoad;
            this.snapPoint = snapPoint;
            this.forwardXZ = forwardXZ;
            this.distanceMeters = distanceMeters;
        }
    }

    /**
     * Check whether the car is on ANY road segment.
     * If not, returns a teleport target to the nearest valid segment.
     */
    public Result check(VehiclePhysicsSystem physics,
                        List<PhysicsRoadSegment> segments) {

        if (physics == null || segments == null || segments.isEmpty()) {
            return new Result(false, null, null, 0f);
        }

        Vector3f pos = physics.getPosition();

        float bestDist2 = Float.POSITIVE_INFINITY;
        Vector3f bestPoint = null;
        Vector3f bestForward = null;
        float bestTolerance = 0f;

        for (PhysicsRoadSegment seg : segments) {

            // Corridor half-width
            float halfWidth =
                    (seg.getLaneCount() * seg.getLaneWidth()) * 0.5f;

            ClosestPoint cp = closestPointOnSegmentXZ(
                    pos,
                    seg.getStartPoint(),
                    seg.getEndPoint()
            );

            float dist = (float) Math.sqrt(cp.dist2);

            // If inside this segment's corridor → ON ROAD, done
            if (dist <= halfWidth) {
                return new Result(false, null, null, dist);
            }

            // Otherwise track nearest segment for teleport
            if (cp.dist2 < bestDist2) {
                bestDist2 = cp.dist2;
                bestTolerance = halfWidth;

                bestPoint = new Vector3f(cp.point.x, pos.y, cp.point.z);

                Vector3f f = seg.getEndPoint()
                        .subtract(seg.getStartPoint());
                f.y = 0f;
                if (f.lengthSquared() < 1e-6f) f.set(0, 0, 1);
                f.normalizeLocal();
                bestForward = f;
            }
        }

        // Not inside ANY segment corridor → off-road
        return new Result(
                true,
                bestPoint,
                bestForward,
                (float) Math.sqrt(bestDist2)
        );
    }

    // ------------------------------------------------------------------

    private static class ClosestPoint {
        final Vector3f point;
        final float dist2;
        ClosestPoint(Vector3f point, float dist2) {
            this.point = point;
            this.dist2 = dist2;
        }
    }

    /**
     * Closest point from P to segment AB in XZ plane.
     */
    private static ClosestPoint closestPointOnSegmentXZ(
            Vector3f p, Vector3f a, Vector3f b) {

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
            return new ClosestPoint(new Vector3f(ax, 0, az), dx * dx + dz * dz);
        }

        float t = (apx * abx + apz * abz) / abLen2;
        t = Math.max(0f, Math.min(1f, t));

        float cx = ax + abx * t;
        float cz = az + abz * t;

        float dx = px - cx;
        float dz = pz - cz;

        return new ClosestPoint(new Vector3f(cx, 0, cz), dx * dx + dz * dz);
    }
}
