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

    private float steeringAngleDeg;
    private static final float MPS_TO_KMH = 3.6f;

    private float offRoadAccumSeconds = 0f;
    private float teleportCooldownSeconds = 0f;

    private static final float OFFROAD_EXTRA_MARGIN = 3.0f;
    private static final float OFFROAD_HOLD_TIME = 0.25f;
    private static final float TELEPORT_COOLDOWN = 1.0f;

    private float currentEngineForce;
    private float currentBrakeForce;

    private List<PhysicsRoadSegment> routeSegments;
    private final SoftRailFollower rail = new SoftRailFollower();

    private float lastCrossTrackMeters = 0f;
    private float lastToleranceMeters = Float.POSITIVE_INFINITY;

    public VehiclePhysicsSystem(PhysicsRigidBody body) {
        this.vehicleBody = body;
        this.config = VehicleConfig.getInstance();
        this.steeringAngleDeg = 0f;
        this.currentEngineForce = 0f;
        this.currentBrakeForce = 0f;
    }

    // ---------------- ROUTE / SAFETY ----------------

    public void setRouteSegments(List<PhysicsRoadSegment> segments) {
        this.routeSegments = segments;
    }

    public boolean isOnRoad() {
        if (routeSegments == null || routeSegments.isEmpty()) return true;
        lastCrossTrackMeters = rail.getCrossTrackMeters(this, routeSegments);
        lastToleranceMeters = rail.getToleranceMeters(routeSegments);
        return lastCrossTrackMeters <= lastToleranceMeters;
    }

    public boolean shouldTeleportBack(float dt) {
        if (routeSegments == null || routeSegments.isEmpty()) return false;

        teleportCooldownSeconds = Math.max(0f, teleportCooldownSeconds - dt);

        lastCrossTrackMeters = rail.getCrossTrackMeters(this, routeSegments);
        lastToleranceMeters  = rail.getToleranceMeters(routeSegments);

        float hardLimit = lastToleranceMeters + OFFROAD_EXTRA_MARGIN;

        if (lastCrossTrackMeters > hardLimit) {
            offRoadAccumSeconds += dt;
        } else {
            offRoadAccumSeconds = 0f;
        }

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

        SoftRailFollower.NearestPoint np =
                rail.getNearestPointAndForward(this, routeSegments);
        if (np == null) return;

        vehicleBody.setLinearVelocity(Vector3f.ZERO);
        vehicleBody.setAngularVelocity(Vector3f.ZERO);

        final float insetMeters = 0.25f;
        final float liftMeters = 0.05f;

        Vector3f forward = np.forwardXZ.clone();
        forward.y = 0f;
        if (forward.lengthSquared() < 1e-6f) forward.set(0, 0, 1);
        forward.normalizeLocal();

        Vector3f right = new Vector3f(forward.z, 0, -forward.x).normalizeLocal();
        float sign = Math.signum(rail.getSignedCrossTrackMeters(this, routeSegments));

        Vector3f target = np.point.add(right.mult(-sign * insetMeters));
        target.y = getPosition().y + liftMeters;

        teleportTo(target, forward);
        vehicleBody.setLinearVelocity(Vector3f.ZERO);
        vehicleBody.setAngularVelocity(Vector3f.ZERO);
    }

    public void teleportTo(Vector3f pos, Vector3f forwardDirXZ) {
        if (pos == null) return;

        vehicleBody.setPhysicsLocation(pos);

        Vector3f dir = forwardDirXZ.clone();
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

    public void steer(float steeringInput) {
        steeringAngleDeg = steeringInput * config.getMaxSteeringAngleDeg();
    }

    public void updateSteering(float dt) {
        if (Math.abs(steeringAngleDeg) < 0.01f) return;

        float speed = vehicleBody.getLinearVelocity().length();
        if (speed < 0.1f) return;

        float steeringRad = (float) Math.toRadians(steeringAngleDeg);
        float L = config.getWheelbase();

        float tan = (float) Math.tan(steeringRad);
        if (Math.abs(tan) < 1e-6f) return;

        float turnRadius = L / tan;
        float angularVelocity = speed / turnRadius;

        Quaternion delta =
                new Quaternion().fromAngleAxis(angularVelocity * dt, Vector3f.UNIT_Y);

        Quaternion rot = delta.mult(vehicleBody.getPhysicsRotation());
        vehicleBody.setPhysicsRotation(rot);

        Vector3f forward = rot.mult(Vector3f.UNIT_Z);
        vehicleBody.setLinearVelocity(forward.mult(speed));
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

    public float calculateStoppingDistance(float speedKmh) {
        float speed = speedKmh / 3.6f;
        float decel = config.getMaxBrakeForce() / config.getMass();
        return decel <= 1e-6f ? Float.POSITIVE_INFINITY
                : (speed * speed) / (2 * decel);
    }

    public Vector3f getForwardDirectionXZ() {
        Vector3f f = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        f.y = 0f;
        return f.lengthSquared() < 1e-6f ? new Vector3f(0,0,1) : f.normalize();
    }
}
