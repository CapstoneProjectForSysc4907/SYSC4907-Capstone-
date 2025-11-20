package group7.capstone.technicalsubsystem;

/// This class is the data layer representing the car's specs
public class VehicleConfig {
    private static VehicleConfig instance;

    private float mass = 1500f; //kg
    private float dragCoefficient = 0.32f; //no units
    private float rollingResistance = 12.0f;   // N per m/s approx.
    private float maxThrottleForce = 4000f;     // Newtons
    private float maxBrakeForce = 9000f;        // Newtons
    private float maxSteeringAngleDeg = 30f;    // degrees
    private float wheelbase = 2.4f; //metres //This is the distacne between front and rear wheels
    private float maxSpeed = 200f; //km/h

    private VehicleConfig(){
    }

    public static VehicleConfig getInstance() {
        if (instance == null) {
            instance = new VehicleConfig();
        }
        return instance;
    }

    public static void setInstance(VehicleConfig instance) {
        VehicleConfig.instance = instance;
    }

    public float getMass() {
        return mass;
    }

    public void setMass(float mass) {
        this.mass = mass;
    }

    public float getDragCoefficient() {
        return dragCoefficient;
    }

    public void setDragCoefficient(float dragCoefficient) {
        this.dragCoefficient = dragCoefficient;
    }

    public float getRollingResistance() {
        return rollingResistance;
    }

    public void setRollingResistance(float rollingResistance) {
        this.rollingResistance = rollingResistance;
    }

    public float getMaxThrottleForce() {
        return maxThrottleForce;
    }

    public void setMaxThrottleForce(float maxThrottleForce) {
        this.maxThrottleForce = maxThrottleForce;
    }

    public float getMaxBrakeForce() {
        return maxBrakeForce;
    }

    public void setMaxBrakeForce(float maxBrakeForce) {
        this.maxBrakeForce = maxBrakeForce;
    }

    public float getMaxSteeringAngleDeg() {
        return maxSteeringAngleDeg;
    }

    public void setMaxSteeringAngleDeg(float maxSteeringAngleDeg) {
        this.maxSteeringAngleDeg = maxSteeringAngleDeg;
    }

    public float getWheelbase() {
        return wheelbase;
    }

    public void setWheelbase(float wheelbase) {
        this.wheelbase = wheelbase;
    }

    public float getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(float maxSpeed) {
        this.maxSpeed = maxSpeed;
    }
}
