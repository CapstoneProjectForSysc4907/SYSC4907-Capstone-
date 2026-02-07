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

    private static final float OFFROAD_HOLD_TIME = 0.25f;
    private static final float TELEPORT_COOLDOWN = 1.0f;

    private float currentEngineForce;
    private float currentBrakeForce;

    private List<PhysicsRoadSegment> routeSegments;
    private final SoftRailFollower rail = new SoftRailFollower();

    public VehiclePhysicsSystem(PhysicsRigidBody body) {
        this.vehicleBody = body;
        this.config = VehicleConfig.getInstance();
        this.currentEngineForce = 0f;
        this.currentBrakeForce = 0f;
    }

    // ---------------- ROUTE / SAFETY ----------------

    public void setRouteSegments(List<PhysicsRoadSegment> segments) {
        this.routeSegments = segments;
    }

    /** True if the car is inside ANY road corridor */
    public boolean isOnRoad() {
        if (routeSegments == null || routeSegments.isEmpty()) return true;

        SoftRailFollower.Result r = rail.check(this, routeSegments);
        return !r.offRoad;
    }

    /** Time-based decision: have we been off-road long enough to teleport? */
    public boolean shouldTeleportBack(float dt) {
        if (routeSegments == null || routeSegments.isEmpty()) return false;

        teleportCooldownSeconds = Math.max(0f, teleportCooldownSeconds - dt);

        SoftRailFollower.Result r = rail.check(this, routeSegments);

        if (r.offRoad) offRoadAccumSeconds += dt;
        else offRoadAccumSeconds = 0f;

        if (teleportCooldownSeconds > 0f) return false;

        if (offRoadAccumSeconds >= OFFROAD_HOLD_TIME) {
            offRoadAccumSeconds = 0f;
            teleportCooldownSeconds = TELEPORT_COOLDOWN;
            return true;
        }

        return false;
    }

    /** Hard snap back to nearest valid road corridor */
    public void teleportToNearestRoad() {
        if (routeSegments == null || routeSegments.isEmpty()) return;

        SoftRailFollower.Result r = rail.check(this, routeSegments);
        if (!r.offRoad || r.snapPoint == null) return;

        vehicleBody.setLinearVelocity(Vector3f.ZERO);
        vehicleBody.setAngularVelocity(Vector3f.ZERO);

        Vector3f target = r.snapPoint.clone();
        target.y = getPosition().y + 0.05f; // small lift to avoid ground clipping

        teleportTo(target, r.forwardXZ);
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

    // How fast the steering angle can change (deg/sec)
    private static final float STEER_RATE_DEG_PER_SEC = 120f; // try 90–180

    // Speed-based steering limits
    private static final float STEER_FULL_KMH = 10f; // <= this, allow max steering
    private static final float STEER_MIN_KMH  = 80f; // >= this, clamp to min steering
    private static final float STEER_MIN_DEG  = 3.0f; // high-speed cap

    public void steer(float steeringInput) {
        // store input only; actual angle is computed in updateSteering(dt)
        this.steeringInput = clamp(steeringInput, -1f, 1f);
    }

    public void updateSteering(float dt) {
        float speedMps = vehicleBody.getLinearVelocity().length();
        if (speedMps < 0.1f) return;

        float speedKmh = speedMps * MPS_TO_KMH;

        // 1) speed-limited max steering
        float maxAllowedDeg = speedLimitedMaxSteerDeg(speedKmh);

        // 2) target from input
        float targetDeg = steeringInput * maxAllowedDeg;

        // 3) rate-limit changes (prevents snap-spin)
        steeringAngleDeg = moveToward(steeringAngleDeg, targetDeg, STEER_RATE_DEG_PER_SEC * dt);

        if (Math.abs(steeringAngleDeg) < STEER_EPS_DEG) return;

        // 4) apply bicycle-yaw approximation
        float steeringRad = (float) Math.toRadians(steeringAngleDeg);
        float L = config.getWheelbase();

        float tan = (float) Math.tan(steeringRad);
        if (Math.abs(tan) < 1e-6f) return;

        float turnRadius = L / tan;
        float angularVelocity = speedMps / turnRadius;

        Quaternion delta = new Quaternion().fromAngleAxis(angularVelocity * dt, Vector3f.UNIT_Y);

        Quaternion rot = delta.mult(vehicleBody.getPhysicsRotation());
        vehicleBody.setPhysicsRotation(rot);

        // keep speed magnitude, just rotate velocity with the body
        Vector3f forward = rot.mult(Vector3f.UNIT_Z);
        vehicleBody.setLinearVelocity(forward.mult(speedMps));
    }

    private float speedLimitedMaxSteerDeg(float speedKmh) {
        float maxDeg = config.getMaxSteeringAngleDeg();
        float minDeg = Math.min(STEER_MIN_DEG, maxDeg);

        // 0..1 between full and min speed
        float t = (speedKmh - STEER_FULL_KMH) / (STEER_MIN_KMH - STEER_FULL_KMH);
        t = clamp(t, 0f, 1f);

        // ease-out: reduces steering more aggressively as speed rises
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

    public Vector3f getForwardDirectionXZ() {
        Vector3f f = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        f.y = 0f;
        return f.lengthSquared() < 1e-6f ? new Vector3f(0, 0, 1) : f.normalize();
    }

    public float getSpeed() {
        return getSpeedKmh();
    }

    public float calculateStoppingDistance(float speedKmh) {
        float speed = speedKmh / 3.6f;
        float decel = config.getMaxBrakeForce() / config.getMass();
        return decel <= 1e-6f ? Float.POSITIVE_INFINITY : (speed * speed) / (2 * decel);
    }
}
