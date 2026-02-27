package group7.capstone.technicalsubsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.List;

/// DO NOT CALL THIS CLASS FROM OUTSIDE THE SUBSYSTEM
/// Pure physics + safety logic. NO path steering assistance.
public class VehiclePhysicsSystem {

    private final PhysicsRigidBody vehicleBody;
    private final VehicleConfig config;

    private static final float MPS_TO_KMH = 3.6f;

    // --- off-road safety timers ---
    private float offRoadAccumSeconds = 0f;
    private float teleportCooldownSeconds = 0f;

    private static final float OFFROAD_HOLD_TIME = 0.60f;
    private static final float TELEPORT_COOLDOWN = 1.0f;

    private float currentEngineForce;
    private float currentBrakeForce;

    private List<PhysicsRoadSegment> routeSegments;
    private final SoftRailFollower rail = new SoftRailFollower();

    // Cached once-per-frame rail result
    private SoftRailFollower.Result lastRailResult = null;

    public VehiclePhysicsSystem(PhysicsRigidBody body) {
        this.vehicleBody = body;
        this.config = VehicleConfig.getInstance();
        this.currentEngineForce = 0f;
        this.currentBrakeForce = 0f;
    }

    // ---------------- ROUTE / SAFETY ----------------

    public void setRouteSegments(List<PhysicsRoadSegment> segments) {
        this.routeSegments = segments;
        offRoadAccumSeconds = 0f;
        teleportCooldownSeconds = 0f;
        lastRailResult = null;
    }

    /** Call ONCE per frame (before isOnRoad / remaining road / teleport decisions). */
    public void updateRailState(float dt) {
        if (routeSegments == null || routeSegments.isEmpty()) {
            lastRailResult = new SoftRailFollower.Result(false, null, null, 0f, -1, 0f);
            return;
        }
        lastRailResult = rail.check(this, routeSegments);
    }

    public boolean isOnRoad() {
        if (routeSegments == null || routeSegments.isEmpty()) return true;
        if (lastRailResult == null) return true;
        return !lastRailResult.offRoad;
    }

    public boolean shouldTeleportBack(float dt) {
        if (routeSegments == null || routeSegments.isEmpty()) return false;

        teleportCooldownSeconds = Math.max(0f, teleportCooldownSeconds - dt);

        if (lastRailResult == null) return false;

        if (lastRailResult.offRoad) offRoadAccumSeconds += dt;
        else offRoadAccumSeconds = 0f;

        if (teleportCooldownSeconds > 0f) return false;

        if (offRoadAccumSeconds >= OFFROAD_HOLD_TIME) {
            offRoadAccumSeconds = 0f;
            teleportCooldownSeconds = TELEPORT_COOLDOWN;
            return true;
        }
        return false;
    }

    public void teleportToNearestRoad() {
        if (routeSegments == null || routeSegments.isEmpty()) return;
        if (lastRailResult == null || !lastRailResult.offRoad || lastRailResult.snapPoint == null) return;

        vehicleBody.setLinearVelocity(Vector3f.ZERO);
        vehicleBody.setAngularVelocity(Vector3f.ZERO);

        Vector3f target = lastRailResult.snapPoint.clone();
        target.y = getPosition().y + 0.05f;

        teleportTo(target, lastRailResult.forwardXZ);
    }

    private void teleportTo(Vector3f pos, Vector3f forwardDirXZ) {
        if (pos == null) return;

        vehicleBody.setPhysicsLocation(pos);

        Vector3f dir = (forwardDirXZ != null) ? forwardDirXZ.clone() : new Vector3f(0, 0, 1);
        dir.y = 0f;
        if (dir.lengthSquared() < 1e-6f) dir.set(0, 0, 1);
        dir.normalizeLocal();

        Quaternion rot = new Quaternion();
        rot.lookAt(dir, Vector3f.UNIT_Y);
        vehicleBody.setPhysicsRotation(rot);

        vehicleBody.setLinearVelocity(Vector3f.ZERO);
        vehicleBody.setAngularVelocity(Vector3f.ZERO);
        vehicleBody.clearForces();
    }

    /**
     * Hard reset to a known-safe pose.
     * Use this when you first load a route
     */
    public void hardResetTo(Vector3f pos, Vector3f forwardDirXZ) {
        teleportTo(pos, forwardDirXZ);
        currentEngineForce = 0f;
        currentBrakeForce = 0f;
        steeringInput = 0f;
        steeringAngleDeg = 0f;
    }

    /** Remaining road distance ahead in metres. */
    public float getRemainingRoadMeters() {
        if (routeSegments == null || routeSegments.isEmpty()) return Float.POSITIVE_INFINITY;
        if (lastRailResult == null) return Float.POSITIVE_INFINITY;

        int idx = lastRailResult.segmentIndex;
        float t = lastRailResult.tOnSegment;

        if (idx < 0 || idx >= routeSegments.size()) return 0f;

        PhysicsRoadSegment cur = routeSegments.get(idx);

        float remaining = (1f - t) * cur.getLength();
        for (int i = idx + 1; i < routeSegments.size(); i++) {
            remaining += routeSegments.get(i).getLength();
        }
        return remaining;
    }

    public PhysicsRoadSegment getCurrentSegment() {
        return rail.getCurrentSegment();
    }

    // ---------------- ENGINE / MOTION ----------------

    private void applyThrottle(float throttle, float dt) {
        float target = throttle * config.getMaxThrottleForce();
        float maxDelta = config.getMaxAccelRate() * dt;

        if (currentEngineForce < target)
            currentEngineForce = Math.min(currentEngineForce + maxDelta, target);
        else
            currentEngineForce = Math.max(currentEngineForce - maxDelta, target);
    }

    private void applyBrake(float brake, float dt) {
        float target = brake * config.getMaxBrakeForce();
        float maxDelta = config.getMaxBrakeRate() * dt;

        if (currentBrakeForce < target)
            currentBrakeForce = Math.min(currentBrakeForce + maxDelta, target);
        else
            currentBrakeForce = Math.max(currentBrakeForce - maxDelta, target);
    }

    public void changeSpeed(float throttle, float brake, float dt) {
        applyThrottle(throttle, dt);
        applyBrake(brake, dt);

        float speed = vehicleBody.getLinearVelocity().length();

        float drag = config.getDragCoefficient() * speed * speed;
        float rolling = config.getRollingResistance() * speed;
        float netForce = currentEngineForce - drag - rolling;

        if (speed > 0.1f && brake > 0f)
            netForce -= currentBrakeForce;

        Vector3f forward = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        vehicleBody.applyCentralForce(forward.mult(netForce));

        if (speed * MPS_TO_KMH > config.getMaxSpeed()) {
            Vector3f v = vehicleBody.getLinearVelocity().normalize()
                    .mult(config.getMaxSpeed() / MPS_TO_KMH);
            vehicleBody.setLinearVelocity(v);
        }
    }

    // ---------------- STEERING (PLAYER ONLY) ----------------

    private float steeringInput = 0f;    // raw [-1..1]
    private float steeringAngleDeg = 0f; // applied (smoothed)

    private static final float STEER_EPS_DEG = 0.01f;
    private static final float STEER_RATE_DEG_PER_SEC = 120f;

    private static final float STEER_FULL_KMH = 10f;
    private static final float STEER_MIN_KMH  = 80f;
    private static final float STEER_MIN_DEG  = 3.0f;

    public void steer(float steeringInput) {
        this.steeringInput = clamp(steeringInput, -1f, 1f);
    }

    public void updateSteering(float dt) {
        float speedMps = vehicleBody.getLinearVelocity().length();
        if (speedMps < 0.1f) return;

        float speedKmh = speedMps * MPS_TO_KMH;

        float maxAllowedDeg = speedLimitedMaxSteerDeg(speedKmh);
        float targetDeg = steeringInput * maxAllowedDeg;

        steeringAngleDeg = moveToward(steeringAngleDeg, targetDeg, STEER_RATE_DEG_PER_SEC * dt);

        if (Math.abs(steeringAngleDeg) < STEER_EPS_DEG) return;

        float steeringRad = (float) Math.toRadians(steeringAngleDeg);
        float L = config.getWheelbase();

        float tan = (float) Math.tan(steeringRad);
        if (Math.abs(tan) < 1e-6f) return;

        float turnRadius = L / tan;
        float angularVelocity = speedMps / turnRadius;

        Quaternion delta = new Quaternion().fromAngleAxis(angularVelocity * dt, Vector3f.UNIT_Y);

        Quaternion rot = delta.mult(vehicleBody.getPhysicsRotation());
        vehicleBody.setPhysicsRotation(rot);

        Vector3f forward = rot.mult(Vector3f.UNIT_Z);
        vehicleBody.setLinearVelocity(forward.mult(speedMps));
    }

    private float speedLimitedMaxSteerDeg(float speedKmh) {
        float maxDeg = config.getMaxSteeringAngleDeg();
        float minDeg = Math.min(STEER_MIN_DEG, maxDeg);

        float t = (speedKmh - STEER_FULL_KMH) / (STEER_MIN_KMH - STEER_FULL_KMH);
        t = clamp(t, 0f, 1f);
        t = t * t;

        return maxDeg + (minDeg - maxDeg) * t;
    }

    private static float moveToward(float current, float target, float maxDelta) {
        float delta = target - current;
        if (Math.abs(delta) <= maxDelta) return target;
        return current + Math.signum(delta) * maxDelta;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ---------------- ACCESSORS ----------------

    public Vector3f getPosition() {
        return vehicleBody.getPhysicsLocation();
    }

    public float getSpeedKmh() {
        return vehicleBody.getLinearVelocity().length() * MPS_TO_KMH;
    }

    public float getSpeed() {
        return getSpeedKmh();
    }

    public Vector3f getForwardDirectionXZ() {
        Vector3f f = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        f.y = 0f;
        return f.lengthSquared() < 1e-6f ? new Vector3f(0, 0, 1) : f.normalize();
    }

    public float calculateStoppingDistance(float speedKmh) {
        float speed = speedKmh / 3.6f;
        float decel = config.getMaxBrakeForce() / config.getMass();
        return decel <= 1e-6f ? Float.POSITIVE_INFINITY : (speed * speed) / (2 * decel);
    }
}
