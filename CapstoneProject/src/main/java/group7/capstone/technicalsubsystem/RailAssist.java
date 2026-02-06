// ===================== RailAssist (MINIMAL CHANGES) =====================
package group7.capstone.technicalsubsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.List;

public class RailAssist {

    // Tune these
    private final float lateralK = 40f;          // spring strength
    private final float lateralDamping = 12f;    // damping against sideways velocity
    private final float yawK = 8f;               // yaw alignment torque strength
    private final float yawDamping = 2f;         // yaw damping
    private final float maxLateralForce = 8000f;
    private final float maxYawTorque = 4000f;

    // Lookahead for steering smoothing (metres)
    private final float lookAhead = 8f;

    // NEW: safety so we don't "tow cable" the car from far away
    private final float engageDistanceMeters = 12f;

    // NEW: don’t apply yaw torque when basically not moving (prevents low-speed spin/jitter)
    private final float minYawSpeedMps = 1.0f;

    public void apply(PhysicsRigidBody body, List<PhysicsRoadSegment> segs, float dt) {
        if (body == null || segs == null || segs.isEmpty()) return;

        Vector3f pos = body.getPhysicsLocation();

        // 1) Find closest point on polyline
        ClosestResult closest = findClosest(pos, segs);
        if (closest == null) return;

        // NEW: if too far from the road, don't yank it back (free drive / fall off behaviour)
        Vector3f posXZ = new Vector3f(pos.x, 0f, pos.z);
        float distToCenter = closest.point.distance(posXZ);
        if (distToCenter > engageDistanceMeters) return;

        // 2) Desired forward tangent (placeholder lookahead)
        Vector3f desiredForward = computeLookaheadTangent(pos, segs, closest, lookAhead);
        if (desiredForward == null) desiredForward = closest.tangent;

        Vector3f forward = desiredForward.normalize();

        // 3) Lateral correction force (spring + damping)
        Vector3f toCenter = closest.point.subtract(posXZ); // (XZ only)
        toCenter.y = 0f;

        Vector3f lateralDir = new Vector3f(forward.z, 0f, -forward.x).normalize(); // right

        float lateralError = toCenter.dot(lateralDir); // signed
        float lateralForceMag = lateralError * lateralK;

        Vector3f vel = body.getLinearVelocity();
        float lateralVel = vel.dot(lateralDir);
        lateralForceMag += (-lateralVel) * lateralDamping;

        lateralForceMag = FastMath.clamp(lateralForceMag, -maxLateralForce, maxLateralForce);

        body.applyCentralForce(lateralDir.mult(lateralForceMag));

        // 4) Yaw alignment torque (only when moving enough)
        float speed = vel.length();
        if (speed < minYawSpeedMps) return;

        Vector3f currentForward = body.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        currentForward.y = 0f;
        currentForward.normalizeLocal();

        float crossY = currentForward.cross(forward).y;
        float yawError = FastMath.asin(FastMath.clamp(crossY, -1f, 1f));

        float yawTorqueMag = yawError * yawK;

        float angVelY = body.getAngularVelocity().y;
        yawTorqueMag += (-angVelY) * yawDamping;

        yawTorqueMag = FastMath.clamp(yawTorqueMag, -maxYawTorque, maxYawTorque);

        body.applyTorque(new Vector3f(0f, yawTorqueMag, 0f));
        System.out.println("PolyLine force applied" + currentForward + closest.point + yawTorqueMag);
    }

    private Vector3f computeLookaheadTangent(Vector3f pos, List<PhysicsRoadSegment> segs,
                                             ClosestResult closest, float lookAheadMeters) {
        // Placeholder (minimal changes): just use current segment direction
        Vector3f a = closest.segStart;
        Vector3f b = closest.segEnd;

        Vector3f segDir = b.subtract(a);
        float segLen = segDir.length();
        if (segLen < 1e-4f) return null;

        return segDir.normalizeLocal();
    }

    private ClosestResult findClosest(Vector3f pos, List<PhysicsRoadSegment> segs) {
        Vector3f p = new Vector3f(pos.x, 0f, pos.z);

        float bestDist2 = Float.POSITIVE_INFINITY;
        ClosestResult best = null;

        for (PhysicsRoadSegment seg : segs) {
            Vector3f a3 = seg.getStartPoint();
            Vector3f b3 = seg.getEndPoint();

            Vector3f a = new Vector3f(a3.x, 0f, a3.z);
            Vector3f b = new Vector3f(b3.x, 0f, b3.z);

            Vector3f ab = b.subtract(a);
            float abLen2 = ab.dot(ab);
            if (abLen2 < 1e-6f) continue;

            float t = (p.subtract(a)).dot(ab) / abLen2;
            t = FastMath.clamp(t, 0f, 1f);

            Vector3f proj = a.add(ab.mult(t));
            float d2 = proj.distanceSquared(p);

            if (d2 < bestDist2) {
                bestDist2 = d2;
                Vector3f tangent = ab.normalize(); // NOTE: ab is mutated here, but we don't use it afterward
                best = new ClosestResult(proj, tangent, a3, b3);
            }
        }
        return best;
    }

    private static final class ClosestResult {
        final Vector3f point;     // closest point on centreline (XZ)
        final Vector3f tangent;   // direction along road (XZ)
        final Vector3f segStart;
        final Vector3f segEnd;

        ClosestResult(Vector3f point, Vector3f tangent, Vector3f segStart, Vector3f segEnd) {
            this.point = point;
            this.tangent = tangent;
            this.segStart = segStart;
            this.segEnd = segEnd;
        }
    }
}
