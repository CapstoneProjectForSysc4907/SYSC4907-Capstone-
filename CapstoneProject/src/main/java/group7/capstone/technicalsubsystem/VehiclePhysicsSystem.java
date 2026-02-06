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
            Vector3f velDir = vel.normalize(); // safe because speedMps > threshold

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
            Vector3f velDir = vel.normalize();
            Vector3f brakeForceVec = velDir.negate().mult(currentBrakeForce);
            vehicleBody.applyCentralForce(brakeForceVec);
        }

        // ------------------ 4) MAX SPEED CAP ------------------
        float speedKmh = speedMps * metresSecondToKilometresPerHour;

        if (speedKmh > config.getMaxSpeed()) {
            Vector3f limitedVelocity = vel.normalize()
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
        List<Vector3f> forceList = new ArrayList<>();
        forceList.add(vehicleBody.getAngularVelocity().normalizeLocal());
        forceList.add(vehicleBody.getLinearVelocity().normalizeLocal());
        return forceList;
    }

    /// This function, below, actually steers the car
    public void updateSteering(float dt) {

        if (Math.abs(steeringAngleDeg) < 0.01f)
            return;

        float speed = vehicleBody.getLinearVelocity().length();
        if (speed < 0.1f)
            return;

        float steeringRad = (float) Math.toRadians(steeringAngleDeg);
        float L = config.getWheelbase();

        float turnRadius = L / (float) Math.tan(steeringRad);
        float angularVelocity = speed / turnRadius;
        float rotationAmount = angularVelocity * dt;

        Quaternion currentRot = vehicleBody.getPhysicsRotation().clone();
        Quaternion deltaRot = new Quaternion().fromAngleAxis(rotationAmount, Vector3f.UNIT_Y);
        Quaternion newRot = deltaRot.mult(currentRot);
        vehicleBody.setPhysicsRotation(newRot);

        // Force velocity to follow the new forward direction
        Vector3f forward = newRot.mult(Vector3f.UNIT_Z);
        Vector3f currentVel = vehicleBody.getLinearVelocity();
        float correctedSpeed = currentVel.length();
        Vector3f correctedVel = forward.mult(correctedSpeed);
        vehicleBody.setLinearVelocity(correctedVel);
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
