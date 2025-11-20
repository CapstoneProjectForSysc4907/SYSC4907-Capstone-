package group7.capstone.technicalsubsystem;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

/// Carobject is the representation of the car itself. It is a high level representation that interacts with the physics
/// This class is an integration layer between the VehiclePhysicsSystem and the Vechicle Config
/// It's not just a controller because it does define the shape of the car.
/// Use this class to interact with the physics, never call the physics directly
public class CarObject {

    private final String id;
    private final float massOfCar;
    private final VehiclePhysicsSystem physics;
    private final MapObject world;
    private final PhysicsRigidBody body;
    private VehicleConfig config = VehicleConfig.getInstance();

    public CarObject(String id, MapObject world) {
        this.id = id;
        this.massOfCar = config.getMass();
        this.world = world;

        BoxCollisionShape carShape = new BoxCollisionShape(new Vector3f(1f, 0.5f, 2f));
        // This defines the shape of the car. The BoxCollision will make it so it can't go through roads and stuff
        //This above is more of the geometry of the car

        body = new PhysicsRigidBody(carShape, massOfCar);
        //This above, compared to carShape is more of the physics of the car

        body.setPhysicsLocation(new Vector3f(0, 1f, 0));
        //This sets the physical location
        float frictionForRubberOnAsphalt = 0.2f;
        body.setFriction(frictionForRubberOnAsphalt);
        world.getPhysicsSpace().addCollisionObject(body);
        this.physics = new VehiclePhysicsSystem(body);
    }

    public void update(float throttle, float brake, float steering, float dt) {
        physics.steer(steering);
        physics.changeSpeed(throttle, brake);
        physics.updateSteering(dt);
    }
    public Vector3f getPosition() {
        return physics.getPosition();
    }

    public float getSpeed() {
        return physics.getSpeed();
    }

    public String getId() {
        return id;
    }
}
