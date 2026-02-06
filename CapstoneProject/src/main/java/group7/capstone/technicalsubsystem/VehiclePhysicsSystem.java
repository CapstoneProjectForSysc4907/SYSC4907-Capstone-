package group7.capstone.technicalsubsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

/// DO NOT CALL THIS CLASS FROM OUTSIDE THE SUBSYSTEM
/// This class here has all the actual physics in it, a real logic layer. Do not call it, use CarObject.
public class VehiclePhysicsSystem {

    private final PhysicsRigidBody vehicleBody;
    private final VehicleConfig config;

    private float steeringAngleDeg;
    private final float metresSecondToKilometresPerHour = 3.6f;

    private float currentEngineForce;
    private float currentBrakeForce;

    // Small thresholds to avoid jitter / divide-by-zero on tiny speeds
    private static final float MIN_SPEED_FOR_RESIST = 0.05f;
    private static final float MIN_SPEED_FOR_BRAKE  = 0.10f;

    // Steering tuning: prevents “perfect orbit” while still coupling heading to velocity a bit
    private static final float MIN_SPEED_FOR_STEER   = 0.10f;
    private static final float MIN_TAN_STEER         = 1e-4f;
    private static final float VELOCITY_BLEND        = 0.08f; // 0=no coupling, 1=hard snap (DON'T)

    public VehiclePhysicsSystem(PhysicsRigidBody body) {
        this.vehicleBody = body;
        this.config = VehicleConfig.getInstance();
        this.steeringAngleDeg = 0f;
        this.currentEngineForce = 0f;
        this.currentBrakeForce = 0f;
    }

    private void applyThrottle(float throttle, float dt) {
        // Clamp input to sane range
        if (throttle < 0f) throttle = 0f;
        if (throttle > 1f) throttle = 1f;

        float targetForce = throttle * config.getMaxThrottleForce();
        float maxForceChange = config.getMaxAccelRate() * dt;   // NEW config field

        if (currentEngineForce < targetForce) {
            currentEngineForce = Math.min(currentEngineForce + maxForceChange, targetForce);
        } else {
            currentEngineForce = Math.max(currentEngineForce - maxForceChange, targetForce);
        }
    }

    private void applyBrake(float brakeInput, float dt) {
        // Clamp input to sane range
        if (brakeInput < 0f) brakeInput = 0f;
        if (brakeInput > 1f) brakeInput = 1f;

        float targetBrake = brakeInput * config.getMaxBrakeForce();
        float maxBrakeChange = config.getMaxBrakeRate() * dt; // NEW config field

        if (currentBrakeForce < targetBrake) {
            currentBrakeForce = Math.min(currentBrakeForce + maxBrakeChange, targetBrake);
        } else {
            currentBrakeForce = Math.max(currentBrakeForce - maxBrakeChange, targetBrake);
        }
    }

    public void changeSpeed(float throttle, float brake, float dt) {
        /*
          throttle and brake are the percent of force one is using of the max
         */
        applyThrottle(throttle, dt);
        applyBrake(brake, dt);

        // Current velocity (Bullet)
        Vector3f vel = vehicleBody.getLinearVelocity();
        float speedMps = vel.length();

        // Car forward direction (based on current rotation)
        Vector3f forward = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        forward.y = 0f;
        if (forward.lengthSquared() > 1e-6f) forward.normalizeLocal();

        // ------------------ 1) ENGINE FORCE (forward) ------------------
        // Apply engine force in the direction the car is facing
        if (currentEngineForce != 0f) {
            Vector3f engineForceVec = forward.mult(currentEngineForce);
            vehicleBody.applyCentralForce(engineForceVec);
        }

        // ------------------ 2) RESISTIVE FORCES (oppose motion) ------------------
        // Drag and rolling resistance should oppose the direction of travel (velocity)
        if (speedMps > MIN_SPEED_FOR_RESIST) {
            // IMPORTANT: don't normalize() the original 'vel' reference; clone first
            Vector3f velDir = vel.clone().normalizeLocal();

            // Drag ~ v^2 (your coefficient)
            float dragMag = config.getDragCoefficient() * speedMps * speedMps;

            // Rolling resistance ~ v (your coefficient)
            float rollingMag = config.getRollingResistance() * speedMps;

            float resistMag = dragMag + rollingMag;

            if (resistMag > 0f) {
                Vector3f resistForce = velDir.negate().mult(resistMag);
                vehicleBody.applyCentralForce(resistForce);
            }
        }

        // ------------------ 3) BRAKING (oppose motion) ------------------
        // IMPORTANT: brake should push opposite current velocity, NOT "subtract from forward force"
        if (speedMps > MIN_SPEED_FOR_BRAKE && currentBrakeForce > 0f) {
            // IMPORTANT: don't normalize() the original 'vel' reference; clone first
            Vector3f velDir = vel.clone().normalizeLocal();
            Vector3f brakeForceVec = velDir.negate().mult(currentBrakeForce);
            vehicleBody.applyCentralForce(brakeForceVec);
        }

        // ------------------ 4) MAX SPEED CAP ------------------
        float speedKmh = speedMps * metresSecondToKilometresPerHour;

        if (speedKmh > config.getMaxSpeed()) {
            // IMPORTANT: don't normalize() the original 'vel' reference; clone first
            Vector3f limitedVelocity = vel.clone().normalizeLocal()
                    .mult(config.getMaxSpeed() / metresSecondToKilometresPerHour);
            vehicleBody.setLinearVelocity(limitedVelocity);
        }
    }

    public Vector3f getPosition() {
        return vehicleBody.getPhysicsLocation();
    }

    public float getSpeed() {
        return vehicleBody.getLinearVelocity().length() * metresSecondToKilometresPerHour;
    }

    public void debugSpeed (){
        vehicleBody.setLinearVelocity(Vector3f.ZERO);
        vehicleBody.setAngularVelocity(Vector3f.ZERO);
    }

    /// This is the steering wheel. That is to say that this just says how much you have 'turned the wheel'.
    /// It does not steer the car that is updateSteering's job
    public void steer(float steeringInput) {
        steeringAngleDeg = steeringInput * config.getMaxSteeringAngleDeg();
    }

    public List<Vector3f> debugGetForces(){
        // IMPORTANT: don't normalizeLocal() the real physics vectors
        List<Vector3f> forceList = new ArrayList<>();

        Vector3f av = vehicleBody.getAngularVelocity();
        Vector3f lv = vehicleBody.getLinearVelocity();

        forceList.add(av == null ? null : av.clone().normalizeLocal());
        forceList.add(lv == null ? null : lv.clone().normalizeLocal());

        return forceList;
    }

    /// This function, below, actually steers the car
    public void updateSteering(float dt) {

        if (Math.abs(steeringAngleDeg) < 0.01f)
            return;

        Vector3f vel = vehicleBody.getLinearVelocity();
        float speed = vel.length();
        if (speed < MIN_SPEED_FOR_STEER)
            return;

        float steeringRad = (float) Math.toRadians(steeringAngleDeg);
        float L = config.getWheelbase();

        float tan = (float) Math.tan(steeringRad);
        if (Math.abs(tan) < MIN_TAN_STEER)
            return;

        float turnRadius = L / tan;
        float angularVelocity = speed / turnRadius; // rad/s
        float rotationAmount = angularVelocity * dt;

        Quaternion currentRot = vehicleBody.getPhysicsRotation().clone();
        Quaternion deltaRot = new Quaternion().fromAngleAxis(rotationAmount, Vector3f.UNIT_Y);
        Quaternion newRot = deltaRot.mult(currentRot);
        vehicleBody.setPhysicsRotation(newRot);

        // DO NOT hard-snap velocity to forward direction (that creates perfect “orbit” behaviour).
        // If you want coupling, blend gently.
        Vector3f forward = newRot.mult(Vector3f.UNIT_Z);
        forward.y = 0f;
        if (forward.lengthSquared() > 1e-6f) forward.normalizeLocal();

        Vector3f velXZ = new Vector3f(vel.x, 0f, vel.z);
        float sp = velXZ.length();

        if (sp > 1e-6f) {
            Vector3f desiredVel = forward.mult(sp);
            float blend = VELOCITY_BLEND; // try 0.05–0.15
            Vector3f blended = velXZ.mult(1f - blend).add(desiredVel.mult(blend));
            vehicleBody.setLinearVelocity(new Vector3f(blended.x, vel.y, blended.z));
        }
    }

    public float calculateStoppingDistance(float speedKmh) {
        float speed = speedKmh / 3.6f;
        float decel = config.getMaxBrakeForce() / config.getMass();
        return (speed * speed) / (2 * decel);
    }

    // Optional: handy for debugging UI
    public float getCurrentEngineForce() { return currentEngineForce; }
    public float getCurrentBrakeForce()  { return currentBrakeForce; }
}
