package group7.capstone.technicalsubsystem;
import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.material.Material;
import com.jme3.math.Vector3f;

public class CarObject {

    private final String id;
    private final VehiclePhysicsSystem physics;
    private final MapObject world;
    private final PhysicsRigidBody body;

    public CarObject(String id, MapObject world) {
        this.id = id;
        this.world = world;

        BoxCollisionShape carShape = new BoxCollisionShape(new Vector3f(1f, 0.5f, 2f));

        BoxCollisionShape shape = new BoxCollisionShape(new Vector3f(1f, 0.5f, 2f));
        body = new PhysicsRigidBody(shape, 800f);
        body.setPhysicsLocation(new Vector3f(0, 1f, 0));
        body.setFriction(0.8f);
        this.physics = new VehiclePhysicsSystem(body);
    }

    public void accelerate(float throttle) {
        physics.changeSpeed(throttle, 0);
    }

    public void brake(float intensity) {
        physics.changeSpeed(0, intensity);
    }

    public Vector3f getPosition() {
        return physics.getPosition();
    }

    public float getSpeed() {
        return physics.getSpeedKmh();
    }

    public String getId() {
        return id;
    }
}
