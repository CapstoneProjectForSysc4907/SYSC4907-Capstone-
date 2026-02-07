package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.List;

public class SoftRailFollower {

    private int currentSegIndex = 0;

    private float kHeading = 1.25f;
    private float kCross = 0.18f;
    private float lookAheadMeters = 8.0f;
    private float advanceThresholdT = 0.97f;

    private float extraGraceMeters = 0.5f;
    private float fullAssistExtraMeters = 6f;
    private float maxAssist = 1.0f;
    private boolean flipCrossSign = false;

    public SoftRailFollower() {}

    public SoftRailFollower(float kHeading, float kCross, float lookAheadMeters) {
        this.kHeading = kHeading;
        this.kCross = kCross;
        this.lookAheadMeters = lookAheadMeters;
    }

    // ---------- Public helpers used by VehiclePhysicsSystem ----------

    /** Unsigned distance from centreline (metres). */
    public float getCrossTrackMeters(VehiclePhysicsSystem physics, List<PhysicsRoadSegment> segments) {
        return Math.abs(getSignedCrossTrackMeters(physics, segments));
    }

    /** Signed distance from centreline (metres). + means right side of segment (based on segment forward). */
    public float getSignedCrossTrackMeters(VehiclePhysicsSystem physics, List<PhysicsRoadSegment> segments) {
        if (physics == null || segments == null || segments.isEmpty()) return 0f;
        SegmentProj sp = getSegmentProjection(physics.getPosition(), segments);
        return sp.proj.signedError;
    }

    public float getToleranceMeters(List<PhysicsRoadSegment> segments) {
        if (segments == null || segments.isEmpty()) return Float.POSITIVE_INFINITY;
        if (currentSegIndex < 0) currentSegIndex = 0;
        if (currentSegIndex >= segments.size()) currentSegIndex = segments.size() - 1;

        PhysicsRoadSegment seg = segments.get(currentSegIndex);
        float halfRoadWidth = (seg.getLaneCount() * seg.getLaneWidth()) / 2f;
        return halfRoadWidth + extraGraceMeters;
    }

    public NearestPoint getNearestPointAndForward(VehiclePhysicsSystem physics, List<PhysicsRoadSegment> segments) {
        if (physics == null || segments == null || segments.isEmpty()) return null;

        Vector3f p = physics.getPosition();
        SegmentProj sp = getSegmentProjection(p, segments);
        PhysicsRoadSegment seg = sp.seg;

        Vector3f a = seg.getStartPoint();
        Vector3f b = seg.getEndPoint();

        Vector3f nearest = new Vector3f(
                a.x + (b.x - a.x) * sp.proj.t,
                p.y,
                a.z + (b.z - a.z) * sp.proj.t
        );

        Vector3f forward = new Vector3f(b.x - a.x, 0, b.z - a.z);
        if (forward.lengthSquared() < 1e-6f) forward.set(0, 0, 1);
        forward.normalizeLocal();

        return new NearestPoint(nearest, forward);
    }

    // Optional: steer assist behaviour (non-teleport)
    public float applyGuardRail(VehiclePhysicsSystem physics, List<PhysicsRoadSegment> segments, float playerSteering) {
        if (physics == null || segments == null || segments.isEmpty()) return clamp(playerSteering, -1f, 1f);

        SegmentProj sp = getSegmentProjection(physics.getPosition(), segments);
        PhysicsRoadSegment seg = sp.seg;
        Projection proj = sp.proj;

        float crossTrack = Math.abs(proj.signedError);
        float tolerance = getToleranceMeters(segments);

        if (crossTrack <= tolerance) {
            return clamp(playerSteering, -1f, 1f);
        }

        float correction = computeSteeringCmdInternal(physics, seg, proj);

        float fullAssistAt = tolerance + fullAssistExtraMeters;
        float alpha = (crossTrack - tolerance) / Math.max(1e-3f, (fullAssistAt - tolerance));
        alpha = clamp(alpha, 0f, 1f) * clamp(maxAssist, 0f, 1f);

        return clamp(lerp(playerSteering, correction, alpha), -1f, 1f);
    }

    // ---------- Internal projection / tracking ----------

    public static class NearestPoint {
        public final Vector3f point;
        public final Vector3f forwardXZ;
        public NearestPoint(Vector3f point, Vector3f forwardXZ) {
            this.point = point;
            this.forwardXZ = forwardXZ;
        }
    }

    private static class SegmentProj {
        final PhysicsRoadSegment seg;
        final Projection proj;
        SegmentProj(PhysicsRoadSegment seg, Projection proj) {
            this.seg = seg;
            this.proj = proj;
        }
    }

    private SegmentProj getSegmentProjection(Vector3f p, List<PhysicsRoadSegment> segments) {
        if (currentSegIndex < 0) currentSegIndex = 0;
        if (currentSegIndex >= segments.size()) currentSegIndex = segments.size() - 1;

        PhysicsRoadSegment seg = segments.get(currentSegIndex);
        Projection proj = projectPointToSegmentXZ(p, seg.getStartPoint(), seg.getEndPoint());

        if (proj.t > advanceThresholdT && currentSegIndex < segments.size() - 1) {
            currentSegIndex++;
            seg = segments.get(currentSegIndex);
            proj = projectPointToSegmentXZ(p, seg.getStartPoint(), seg.getEndPoint());
        }

        return new SegmentProj(seg, proj);
    }

    private float computeSteeringCmdInternal(VehiclePhysicsSystem physics, PhysicsRoadSegment seg, Projection proj) {
        Vector3f p = physics.getPosition();

        Vector3f a = seg.getStartPoint();
        Vector3f b = seg.getEndPoint();
        Vector3f ab = new Vector3f(b.x - a.x, 0, b.z - a.z);
        float segLen = ab.length();
        Vector3f segForward = (segLen > 1e-6f) ? ab.divide(segLen) : new Vector3f(0, 0, 1);

        float lookAheadT = proj.t + (lookAheadMeters / Math.max(segLen, 1e-3f));
        lookAheadT = clamp(lookAheadT, 0f, 1f);

        Vector3f target = new Vector3f(
                a.x + (b.x - a.x) * lookAheadT,
                p.y,
                a.z + (b.z - a.z) * lookAheadT
        );

        Vector3f desired = new Vector3f(target.x - p.x, 0, target.z - p.z);
        if (desired.lengthSquared() < 1e-6f) desired = segForward.clone();
        desired.normalizeLocal();

        Vector3f carForward = physics.getForwardDirectionXZ();
        float headingErr = signedAngleXZ(carForward, desired);

        float crossErr = proj.signedError;
        if (flipCrossSign) crossErr = -crossErr;

        float cmd = (kHeading * headingErr) - (kCross * crossErr);
        return clamp(cmd, -1f, 1f);
    }

    private static class Projection {
        final float t;
        final float signedError;
        Projection(float t, float signedError) {
            this.t = t;
            this.signedError = signedError;
        }
    }

    private static Projection projectPointToSegmentXZ(Vector3f p, Vector3f a, Vector3f b) {
        Vector3f ap = new Vector3f(p.x - a.x, 0, p.z - a.z);
        Vector3f ab = new Vector3f(b.x - a.x, 0, b.z - a.z);

        float abLen2 = ab.dot(ab);
        if (abLen2 < 1e-6f) return new Projection(0f, 0f);

        float t = ap.dot(ab) / abLen2;
        t = clamp(t, 0f, 1f);

        float segLen = (float) Math.sqrt(abLen2);
        Vector3f forward = (segLen > 1e-6f) ? ab.divide(segLen) : new Vector3f(0, 0, 1);

        Vector3f right = new Vector3f(forward.z, 0, -forward.x);
        if (right.lengthSquared() < 1e-6f) right.set(1, 0, 0);
        right.normalizeLocal();

        Vector3f err = new Vector3f(ap.x - ab.x * t, 0, ap.z - ab.z * t);

        float signedError = err.dot(right);
        return new Projection(t, signedError);
    }

    private static float signedAngleXZ(Vector3f from, Vector3f to) {
        float fx = from.x, fz = from.z;
        float tx = to.x, tz = to.z;

        float crossY = fx * tz - fz * tx;
        float dot = fx * tx + fz * tz;

        return (float) Math.atan2(crossY, dot);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
}
