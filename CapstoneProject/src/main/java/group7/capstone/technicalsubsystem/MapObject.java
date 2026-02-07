package group7.capstone.technicalsubsystem;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

public class MapObject {

    private final PhysicsSpace physicsSpace;

    public MapObject() {
        physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        physicsSpace.setGravity(new Vector3f(0, -9.81f, 0));
        addGroundPlane();
    }

    private void addGroundPlane() {
        // Big static floor at y=0
        BoxCollisionShape groundShape =
                new BoxCollisionShape(new Vector3f(10000f, 0.5f, 10000f));

        PhysicsRigidBody ground = new PhysicsRigidBody(groundShape, 0f);
        ground.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        physicsSpace.addCollisionObject(ground);
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    public void step(float dt) {
        physicsSpace.update(dt);
    }
}
