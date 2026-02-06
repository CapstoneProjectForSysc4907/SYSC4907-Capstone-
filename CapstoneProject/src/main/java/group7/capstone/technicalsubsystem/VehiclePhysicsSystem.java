package group7.capstone.technicalsubsystem;

import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

/// DO NOT CALL THIS CLASS FROM OUTSIDE THE SUBSYSTEM
/// This class here has all the actual physics in it, a real logic layer. Do not call it, use CarObject.

public class VehiclePhysicsSystem {

    private final PhysicsRigidBody vehicleBody;
    private VehicleConfig config;
    private float steeringAngleDeg;
    private final float metresSecondToKilometresPerHour = 3.6f;
    private float currentEngineForce;
    private float currentBrakeForce;

    public VehiclePhysicsSystem(PhysicsRigidBody body) {
        this.vehicleBody = body;
        config  = VehicleConfig.getInstance();
        steeringAngleDeg = 0f;
        currentEngineForce = 0f;
        currentBrakeForce = 0f;
    }

    private void applyThrottle(float throttle, float dt) {
        float targetForce = throttle * config.getMaxThrottleForce();

        float maxForceChange = config.getMaxAccelRate() * dt;   // NEW config field

        if (currentEngineForce < targetForce) {
            currentEngineForce = Math.min(currentEngineForce + maxForceChange, targetForce);
        } else {
            currentEngineForce = Math.max(currentEngineForce - maxForceChange, targetForce);
        }
    }

    private void applyBrake(float brakeInput, float dt) {
        float targetBrake = brakeInput * config.getMaxBrakeForce();

        float maxBrakeChange = config.getMaxBrakeRate() * dt; // NEW config field

        if (currentBrakeForce < targetBrake) {
            currentBrakeForce = Math.min(currentBrakeForce + maxBrakeChange, targetBrake);
        } else {
            currentBrakeForce = Math.max(currentBrakeForce - maxBrakeChange, targetBrake);
        }
    }


    public void changeSpeed(float throttle, float brake, float dt) {
        /* throttle and brake are the percent of force one is using of the max

        /*

         */
        applyThrottle(throttle, dt);
        applyBrake(brake, dt);

        float currentSpeed = vehicleBody.getLinearVelocity().length(); //metres per second
        float engineForce = currentEngineForce; //Newtons
        float brakeForce  = currentBrakeForce; //Newtons

        float drag = config.getDragCoefficient() * currentSpeed * currentSpeed; //No unit
        float rolling = config.getRollingResistance() * currentSpeed; //Newtons
        //Rolling resistance has to do with the defomration of the tires and such
        //Unlike friciton it is constant and does not scale with speed

        float netForce = engineForce - drag - rolling;

        //This if statement below only lets one brake if the car is moving
        if (currentSpeed > 0.1f && brake > 0f) {
            netForce -= brakeForce;
        }

        float acceleration = netForce / config.getMass();
        float bulletForce = acceleration * config.getMass();
        //It's just called bullet force to be sent to the bullet physics engine

        Vector3f forward = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        /*
        This above is imporant
        Vector3f.UNIT_Z is the direction of the default car rotation. It defines forward
        It gets  multiplied in 'mult' by the cars rotation to figure out where it's facing
        This line makes it so that the brakes/accelration is always facing forward.
         */
        Vector3f appliedForce = forward.mult(bulletForce);
        //This line above takes that rotated vector and expands it to include the force
        vehicleBody.applyCentralForce(appliedForce);

        float speed = currentSpeed * metresSecondToKilometresPerHour; // Output is m/s

        //This whole block below is to stop one from going above the car's max speed
        if (speed > config.getMaxSpeed()) {
            Vector3f limitedVelocity = vehicleBody.getLinearVelocity()
                    .normalize() //This takes out the speed componet if the vector has speed and direction
                    .mult(config.getMaxSpeed() / metresSecondToKilometresPerHour);
            vehicleBody.setLinearVelocity(limitedVelocity);
            //Linear velocity is a way of saying current speed, with respect to the vector
        }

    }

    public Vector3f getPosition() {
        return vehicleBody.getPhysicsLocation();
    }

    public float getSpeed() {
        return vehicleBody.getLinearVelocity().length() * metresSecondToKilometresPerHour; // Output is metres per second
    }

    /// This is the steering wheel. That is to say that this just says how much you have 'turned the wheel'.
    /// It does not steer the car that is updateSteering's job
    public void steer(float steeringInput) {
        steeringAngleDeg = steeringInput * config.getMaxSteeringAngleDeg();
    }
    /// This function, below, actually steers the car
    public void updateSteering(float dt) {
        //Above, float dt reprsents the time since the last physics update. Dt being delta time

        //This if statement, below, says if no steering, do not update
        if (Math.abs(steeringAngleDeg) < 0.01f)
            return;

        float speed = vehicleBody.getLinearVelocity().length();
        //Below, says, if not moving do not update
        if (speed < 0.1f)
            return;

        float steeringRad = (float)Math.toRadians(steeringAngleDeg);
        float L = config.getWheelbase(); //This is the distance between the front and rear axles

        float turnRadius = L / (float)Math.tan(steeringRad);

        float angularVelocity = speed / turnRadius;

        float rotationAmount = angularVelocity * dt;

        //This below actually changes the rotation
        Quaternion currentRot = vehicleBody.getPhysicsRotation().clone();
        Quaternion deltaRot = new com.jme3.math.Quaternion().fromAngleAxis(rotationAmount, Vector3f.UNIT_Y);
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

}
