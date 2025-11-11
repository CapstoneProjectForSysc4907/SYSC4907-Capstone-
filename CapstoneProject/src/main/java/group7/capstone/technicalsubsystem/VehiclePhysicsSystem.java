package group7.capstone.technicalsubsystem;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;


public class VehiclePhysicsSystem {

    //This is the manager that runs the internal simulation
    //This below is vechcile subject to physics, it's the mass and motion properties
    private final PhysicsSpace physicsSpace;
    private final PhysicsRigidBody vehicleBody;
    private final PhysicsRigidBody groundBody;

    private static final float ENGINE_FORCE = 500f; // Newtons
    private static final float BRAKE_FORCE = 300f;  // Newtons
    private static final float MASS = 800f; // kilograms
    private static final float MASS_VEHICLE = 800f;       // kg
    private static final float STEER_TORQUE = 2500f;      // N·m
    private static final Vector3f GRAVITY   = new Vector3f(0, -9.81f, 0);

    private static final Vector3f VEHICLE_SIZE_HALF = new Vector3f(1.0f, 0.5f, 2.0f); // 2×1×4 m
    private static final Vector3f GROUND_SIZE_HALF  = new Vector3f(100f, 0.5f, 100f); // big static slab


    private int speed;

    public  VehiclePhysicsSystem() {
        physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        physicsSpace.setGravity(GRAVITY);

        BoxCollisionShape groundShape = new BoxCollisionShape(GROUND_SIZE_HALF);
         groundBody = new PhysicsRigidBody(groundShape, 0f);

        BoxCollisionShape carShape = new BoxCollisionShape(VEHICLE_SIZE_HALF);
         carShape = new BoxCollisionShape(new Vector3f(1.0f, 0.5f, 2.0f));
         vehicleBody = new PhysicsRigidBody(carShape, 800f);
        vehicleBody.setPhysicsLocation(new Vector3f(0, 1f, 0));

        vehicleBody.setFriction(0.8f);
        vehicleBody.setDamping(0.02f, 0.2f);
        physicsSpace.addCollisionObject(vehicleBody);

    }


    public void changeSpeed (float throttle, float brake){

        float netForce = (ENGINE_FORCE * throttle) - (BRAKE_FORCE * brake);

        Vector3f forward = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);

        Vector3f force = forward.mult(netForce);
        vehicleBody.applyCentralForce(force);
    }

    public void stepSimulation(float dt) {
        physicsSpace.update(dt);
    }

    public Vector3f getPosition() {
        return vehicleBody.getPhysicsLocation();
    }

    public float getSpeedKmh() {
        return vehicleBody.getLinearVelocity().length() * 3.6f;
    }

}


