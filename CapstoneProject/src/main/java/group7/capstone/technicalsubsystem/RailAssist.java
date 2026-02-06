// ===================== RailAssist (ALWAYS-ON RAIL, DISTANCE-RAMPED PD + OPTIONAL FAR BRAKE) =====================
package group7.capstone.technicalsubsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;

import java.util.List;

public class RailAssist {

    // ===== Base tuning (near the road) =====
    private final float lateralK = 40f;          // spring strength near road
    private final float lateralDamping = 12f;    // damping near road
    private final float yawK = 8f;              // yaw alignment torque strength
    private final float yawDamping = 2f;        // yaw damping

    // Debug-friendly last closest point (CLONED so callers can't mutate it)
    private Vector3f closepoint = null;

    // Caps (still useful to prevent physics blowups)
    private final float maxLateralForce = 8000f;
    private final float maxYawTorque = 4000f;

    // Lookahead for steering smoothing (metres)
    private final float lookAhead = 8f;

    // Don’t apply yaw torque when basically not moving (prevents low-speed spin/jitter)
    private final float minYawSpeedMps = 1.0f;

    // ===== distance-based ramping =====
    // "Deadband" around the centreline where we don't overreact (metres)
    private final float softBandMeters = 1.5f;

    // How aggressively gains increase with distance (tune these)
    private final float kGainPerMeter = 0.20f;        // +20% K per metre beyond softBand
    private final float dGainPerMeter = 0.35f;        // +35% D per metre beyond softBand

    // Optional: additional nonlinear boost at extreme distances
    private final float kQuadPerMeter2 = 0.0020f;     // small quadratic term for K
    private final float dQuadPerMeter2 = 0.0035f;     // small quadratic term for D

    // ===== Optional: far-from-road speed limiting via rail force (prevents “orbit at high speed”) =====
    private final boolean enableFarBrake = true;
    private final float farBrakeStartMeters = 6f;     // start braking when |lateralError| beyond this
    private final float farBrakeMaxMeters = 25f;      // at/after this, strongest braking
    private final float farBrakeForceMax = 6000f;     // N-ish (depends on your mass scale)
    private final float farTargetSpeed = 20f;         // target speed when very far (m/s-ish)

    // ===== Longitudinal pull toward closest point (prevents orbit / lets you converge) =====
    private final float longitudinalK = 25f;          // spring along road tangent
    private final float longitudinalDamping = 10f;    // damping along road tangent
    private final float maxLongitudinalForce = 6000f;

    // ===== Segment hysteresis (prevents flapping / snapping to early segment) =====
    private int lastSegIndex = -1;
    private final float segIndexPenalty = 0.05f;      // penalty per |i-lastSegIndex| added to d^2 score

    // ===== Debug snapshot (last frame) =====
    private DebugSnapshot lastDebug; // null if nothing produced yet

    // ===== Extra debug toggle: print raw projection details =====
    private static final boolean DEBUG_PROJECTION = false;

    public DebugSnapshot getLastDebug() { return lastDebug; }
    public boolean hasLastDebug() { return lastDebug != null; }
    public void clearLastDebug() { lastDebug = null; closepoint = null; }


    public void apply(PhysicsRigidBody body, List<PhysicsRoadSegment> segs, float dt) {

        //System.out.println("Car object form rail " + body.getObjectId());

        DebugSnapshot dbg = new DebugSnapshot();
        dbg.dt = dt;
        dbg.segsSize = (segs == null) ? 0 : segs.size();
        dbg.engaged = false;

        if (body == null || segs == null || segs.isEmpty()) {
            dbg.reason = DebugSnapshot.Reason.NO_BODY_OR_SEGS;
            lastDebug = dbg;
            return;
        }

        Vector3f pos = body.getPhysicsLocation();
        dbg.location = pos.clone();

        Vector3f vel = body.getLinearVelocity();
        dbg.velocity = (vel == null) ? null : vel.clone();
        dbg.speed = (dbg.velocity == null) ? 0f : dbg.velocity.length();

        // 1) Find closest point on polyline
        ClosestResult closest = findClosest(pos, segs);
        if (closest == null) {
            dbg.reason = DebugSnapshot.Reason.NO_CLOSEST;
            lastDebug = dbg;
            closepoint = null;
            return;
        }

        // IMPORTANT: clone so nothing external can accidentally "freeze" it via shared refs
        closepoint = closest.point.clone();

        // Update hysteresis state
        lastSegIndex = closest.segIndex;

        dbg.closestPoint = closest.point.clone();
        dbg.segStart = closest.segStart.clone();
        dbg.segEnd = closest.segEnd.clone();

        // segment diagnostics
        dbg.segIndex = closest.segIndex;
        dbg.segT = closest.t;

        Vector3f posXZ = new Vector3f(pos.x, 0f, pos.z);
        float distToCenter = closest.point.distance(posXZ);
        dbg.distToCenter = distToCenter;

        // 2) Desired forward tangent (lookahead placeholder)
        Vector3f desiredForward = computeLookaheadTangent(pos, segs, closest, lookAhead);
        if (desiredForward == null) desiredForward = closest.tangent;

        Vector3f forward = desiredForward.clone();
        forward.y = 0f;
        if (forward.lengthSquared() < 1e-8f) {
            dbg.reason = DebugSnapshot.Reason.BAD_FORWARD;
            lastDebug = dbg;
            return;
        }
        forward.normalizeLocal();
        dbg.desiredForward = forward.clone();

        // IMPORTANT: use the ACTUAL segment tangent for lateral basis so the frame doesn't drift
        Vector3f segTangent = closest.tangent.clone();
        segTangent.y = 0f;
        if (segTangent.lengthSquared() < 1e-8f) {
            segTangent.set(forward);
        }
        segTangent.normalizeLocal();

        // 3) Lateral correction force (distance-ramped spring + distance-ramped damping)
        // NOTE: closest.point and posXZ are both XZ, so this is safe.
        Vector3f toCenter = closest.point.subtract(posXZ); // (XZ only)
        toCenter.y = 0f;
        dbg.toCenter = toCenter.clone();

        // Lateral axis based on segment tangent (stable)
        Vector3f lateralDir = new Vector3f(segTangent.z, 0f, -segTangent.x);
        if (lateralDir.lengthSquared() < 1e-8f) {
            dbg.reason = DebugSnapshot.Reason.BAD_LATERAL_DIR;
            lastDebug = dbg;
            return;
        }
        lateralDir.normalizeLocal();
        dbg.lateralDir = lateralDir.clone();

        float lateralError = toCenter.dot(lateralDir); // signed metres-ish
        dbg.lateralError = lateralError;

        float absErr = FastMath.abs(lateralError);

        // --- Gain ramp: starts outside a soft band, then grows unbounded (but we still cap final force) ---
        float excess = Math.max(0f, absErr - softBandMeters);

        // linear + quadratic gain multiplier
        float kMult = 1f + (kGainPerMeter * excess) + (kQuadPerMeter2 * excess * excess);
        float dMult = 1f + (dGainPerMeter * excess) + (dQuadPerMeter2 * excess * excess);

        float kEff = lateralK * kMult;
        float dEff = lateralDamping * dMult;

        // Spring term
        float lateralForceMag = lateralError * kEff;

        // Damping term (lateral velocity in lateralDir)
        float lateralVel = (dbg.velocity == null) ? 0f : dbg.velocity.dot(lateralDir);
        dbg.lateralVel = lateralVel;

        lateralForceMag += (-lateralVel) * dEff;

        // Final clamp (prevents Bullet instability)
        lateralForceMag = FastMath.clamp(lateralForceMag, -maxLateralForce, maxLateralForce);
        dbg.lateralForceMag = lateralForceMag;

        Vector3f appliedLat = lateralDir.mult(lateralForceMag);
        dbg.appliedLateralForce = appliedLat.clone();
        body.applyCentralForce(appliedLat);

        // ===== Longitudinal correction (pull along the segment toward closest point) =====
        float longitudinalError = toCenter.dot(segTangent);
        dbg.longitudinalError = longitudinalError;

        float longitudinalVel = (dbg.velocity == null) ? 0f : dbg.velocity.dot(segTangent);
        dbg.longitudinalVel = longitudinalVel;

        float longForceMag = (longitudinalError * longitudinalK) + (-longitudinalVel * longitudinalDamping);
        longForceMag = FastMath.clamp(longForceMag, -maxLongitudinalForce, maxLongitudinalForce);
        dbg.longitudinalForceMag = longForceMag;

        Vector3f appliedLong = segTangent.mult(longForceMag);
        dbg.appliedLongitudinalForce = appliedLong.clone();
        body.applyCentralForce(appliedLong);

        // 3b) Optional far braking / speed limiting
        if (enableFarBrake && dbg.velocity != null) {
            float farT = inverseLerp(farBrakeStartMeters, farBrakeMaxMeters, absErr); // 0..1
            if (farT > 0f) {
                Vector3f velXZ = new Vector3f(dbg.velocity.x, 0f, dbg.velocity.z);
                float speedXZ = velXZ.length();

                if (speedXZ > 1e-3f) {
                    Vector3f velDir = velXZ.normalize();

                    float overSpeed = Math.max(0f, speedXZ - farTargetSpeed);
                    float brakeMag = FastMath.clamp(overSpeed * 300f * farT, 0f, farBrakeForceMax);

                    Vector3f brake = velDir.mult(-brakeMag);
                    body.applyCentralForce(brake);

                    dbg.appliedBrakeForce = brake.clone();
                    dbg.brakeT = farT;
                }
            }
        }

        // 4) Yaw alignment torque (only when moving enough)
        if (dbg.speed < minYawSpeedMps) {
            dbg.reason = DebugSnapshot.Reason.TOO_SLOW_FOR_YAW;
            dbg.engaged = true;
            lastDebug = dbg;
            return;
        }

        Vector3f currentForward = body.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        currentForward.y = 0f;
        if (currentForward.lengthSquared() < 1e-8f) {
            dbg.reason = DebugSnapshot.Reason.BAD_CURRENT_FORWARD;
            dbg.engaged = true;
            lastDebug = dbg;
            return;
        }
        currentForward.normalizeLocal();
        dbg.currentForward = currentForward.clone();

        // Use atan2(cross, dot) for a stable signed yaw error
        float crossY = currentForward.cross(forward).y;
        dbg.crossY = crossY;

        float dot = FastMath.clamp(currentForward.dot(forward), -1f, 1f);
        float yawError = FastMath.atan2(crossY, dot);
        dbg.yawError = yawError;

        float yawMult = 1f + 0.10f * excess; // mild
        float yawTorqueMag = yawError * (yawK * yawMult);

        float angVelY = body.getAngularVelocity().y;
        dbg.angVelY = angVelY;

        yawTorqueMag += (-angVelY) * yawDamping;
        yawTorqueMag = FastMath.clamp(yawTorqueMag, -maxYawTorque, maxYawTorque);
        dbg.yawTorqueMag = yawTorqueMag;

        Vector3f appliedTorque = new Vector3f(0f, yawTorqueMag, 0f);
        dbg.appliedTorque = appliedTorque.clone();
        body.applyTorque(appliedTorque);

        dbg.engaged = true;
        dbg.reason = DebugSnapshot.Reason.OK;
        lastDebug = dbg;
    }

    private static float inverseLerp(float a, float b, float v) {
        if (b <= a) return 0f;
        return FastMath.clamp((v - a) / (b - a), 0f, 1f);
    }

    private Vector3f computeLookaheadTangent(Vector3f pos, List<PhysicsRoadSegment> segs,
                                             ClosestResult closest, float lookAheadMeters) {
        // Placeholder: just use current segment direction (XZ)
        Vector3f a = closest.segStart;
        Vector3f b = closest.segEnd;

        Vector3f segDir = new Vector3f(b.x - a.x, 0f, b.z - a.z);
        if (segDir.lengthSquared() < 1e-8f) return null;

        return segDir.normalizeLocal();
    }

    /**
     * Closest point on segment AB to point P in XZ plane.
     * IMPORTANT: never uses int casts; never mutates inputs; returns a NEW vector.
     */
    private static Vector3f closestPointOnSegmentXZ(Vector3f a, Vector3f b, Vector3f p, OutFloat outT) {
        // XZ-only endpoints and point
        Vector3f a2 = new Vector3f(a.x, 0f, a.z);
        Vector3f b2 = new Vector3f(b.x, 0f, b.z);
        Vector3f p2 = new Vector3f(p.x, 0f, p.z);

        Vector3f ab = b2.subtract(a2);
        float abLen2 = ab.dot(ab);
        if (abLen2 < 1e-6f) {
            if (outT != null) outT.v = 0f;
            return a2;
        }

        Vector3f ap = p2.subtract(a2);

        float t = ap.dot(ab) / abLen2;      // MUST be float
        t = FastMath.clamp(t, 0f, 1f);

        if (outT != null) outT.v = t;

        // proj = a + ab * t
        return a2.add(ab.mult(t));
    }

    private ClosestResult findClosest(Vector3f pos, List<PhysicsRoadSegment> segs) {
        Vector3f p = new Vector3f(pos.x, 0f, pos.z);

        float bestScore = Float.POSITIVE_INFINITY;
        float bestDist2 = Float.POSITIVE_INFINITY;
        ClosestResult best = null;

        for (int i = 0; i < segs.size(); i++) {
            PhysicsRoadSegment seg = segs.get(i);
            if (seg == null) continue;

            Vector3f a3 = seg.getStartPoint();
            Vector3f b3 = seg.getEndPoint();
            if (a3 == null || b3 == null) continue;

            // Compute projection safely
            OutFloat tOut = new OutFloat();
            Vector3f proj = closestPointOnSegmentXZ(a3, b3, p, tOut);
            float t = tOut.v;

            float d2 = proj.distanceSquared(p);

            // Hysteresis: penalize jumping far in polyline index
            float score = d2;
            if (lastSegIndex >= 0) {
                int di = Math.abs(i - lastSegIndex);
                score += di * segIndexPenalty;
            }

            if (DEBUG_PROJECTION && i == 0) {
                System.out.println("[RailAssist] seg0 proj t=" + t + " proj=" + proj + " p=" + p + " d2=" + d2);
            }

            if (score < bestScore || (score == bestScore && d2 < bestDist2)) {
                bestScore = score;
                bestDist2 = d2;

                // Tangent from original endpoints (XZ)
                Vector3f tangent = new Vector3f(b3.x - a3.x, 0f, b3.z - a3.z);
                if (tangent.lengthSquared() < 1e-8f) continue;
                tangent.normalizeLocal();

                // Store XZ endpoints in result (cloned)
                Vector3f aXZ = new Vector3f(a3.x, 0f, a3.z);
                Vector3f bXZ = new Vector3f(b3.x, 0f, b3.z);

                best = new ClosestResult(
                        proj.clone(),          // closest point (XZ)
                        tangent.clone(),       // tangent (XZ)
                        aXZ,                   // segStart (XZ)
                        bXZ,                   // segEnd (XZ)
                        i,
                        t
                );
            }
        }
        return best;
    }

    public Vector3f getPoint() {
        return (closepoint == null) ? null : closepoint.clone();
    }

    // tiny helper to return t without allocating arrays
    private static final class OutFloat { float v; }

    private static final class ClosestResult {
        final Vector3f point;     // closest point on centreline (XZ)
        final Vector3f tangent;   // direction along road (XZ)
        final Vector3f segStart;  // XZ
        final Vector3f segEnd;    // XZ

        final int segIndex;
        final float t;            // 0..1

        ClosestResult(Vector3f point,
                      Vector3f tangent,
                      Vector3f segStart,
                      Vector3f segEnd,
                      int segIndex,
                      float t) {
            this.point = point;
            this.tangent = tangent;
            this.segStart = segStart;
            this.segEnd = segEnd;
            this.segIndex = segIndex;
            this.t = t;
        }
    }

    // ===== Data-only debug object =====
    public static final class DebugSnapshot {
        public enum Reason {
            OK,
            NO_BODY_OR_SEGS,
            NO_CLOSEST,
            BAD_FORWARD,
            BAD_LATERAL_DIR,
            TOO_SLOW_FOR_YAW,
            BAD_CURRENT_FORWARD
        }

        // meta
        private float dt;
        private int segsSize;
        private boolean engaged;
        private Reason reason;

        // kinematics
        private Vector3f location;
        private Vector3f velocity;
        private float speed;

        // closest + geometry
        private Vector3f closestPoint;
        private float distToCenter;
        private Vector3f segStart;
        private Vector3f segEnd;

        // segment diagnostics
        private int segIndex;
        private float segT;

        // steering vectors
        private Vector3f desiredForward;
        private Vector3f currentForward;
        private Vector3f lateralDir;
        private Vector3f toCenter;

        // lateral terms
        private float lateralError;
        private float lateralVel;
        private float lateralForceMag;
        private Vector3f appliedLateralForce;

        // longitudinal terms
        private float longitudinalError;
        private float longitudinalVel;
        private float longitudinalForceMag;
        private Vector3f appliedLongitudinalForce;

        // yaw terms
        private float crossY;
        private float yawError;
        private float angVelY;
        private float yawTorqueMag;
        private Vector3f appliedTorque;

        // optional braking terms
        private Vector3f appliedBrakeForce;
        private float brakeT;

        // ===== Getters =====
        public float getDt() { return dt; }
        public int getSegsSize() { return segsSize; }
        public boolean isEngaged() { return engaged; }
        public Reason getReason() { return reason; }

        public Vector3f getLocation() { return location == null ? null : location.clone(); }
        public Vector3f getVelocity() { return velocity == null ? null : velocity.clone(); }
        public float getSpeed() { return speed; }

        public Vector3f getClosestPoint() { return closestPoint == null ? null : closestPoint.clone(); }
        public float getDistToCenter() { return distToCenter; }
        public Vector3f getSegStart() { return segStart == null ? null : segStart.clone(); }
        public Vector3f getSegEnd() { return segEnd == null ? null : segEnd.clone(); }

        public int getSegIndex() { return segIndex; }
        public float getSegT() { return segT; }

        public Vector3f getDesiredForward() { return desiredForward == null ? null : desiredForward.clone(); }
        public Vector3f getCurrentForward() { return currentForward == null ? null : currentForward.clone(); }
        public Vector3f getLateralDir() { return lateralDir == null ? null : lateralDir.clone(); }
        public Vector3f getToCenter() { return toCenter == null ? null : toCenter.clone(); }

        public float getLateralError() { return lateralError; }
        public float getLateralVel() { return lateralVel; }
        public float getLateralForceMag() { return lateralForceMag; }
        public Vector3f getAppliedLateralForce() { return appliedLateralForce == null ? null : appliedLateralForce.clone(); }

        public float getLongitudinalError() { return longitudinalError; }
        public float getLongitudinalVel() { return longitudinalVel; }
        public float getLongitudinalForceMag() { return longitudinalForceMag; }
        public Vector3f getAppliedLongitudinalForce() { return appliedLongitudinalForce == null ? null : appliedLongitudinalForce.clone(); }

        public float getCrossY() { return crossY; }
        public float getYawError() { return yawError; }
        public float getAngVelY() { return angVelY; }
        public float getYawTorqueMag() { return yawTorqueMag; }
        public Vector3f getAppliedTorque() { return appliedTorque == null ? null : appliedTorque.clone(); }

        public Vector3f getAppliedBrakeForce() { return appliedBrakeForce == null ? null : appliedBrakeForce.clone(); }
        public float getBrakeT() { return brakeT; }
    }
}
