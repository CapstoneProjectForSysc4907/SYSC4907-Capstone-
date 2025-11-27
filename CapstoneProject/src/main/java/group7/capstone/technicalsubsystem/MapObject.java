package group7.capstone.technicalsubsystem;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

/// DO NOT CALL THIS CLASS FROM OUTSIDE THE SUBSYSTEM
public class MapObject {

    private final PhysicsSpace physicsSpace;
    private final List<RoadSegment> activeSegments = new ArrayList<>();

    public MapObject() {
        physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        physicsSpace.setGravity(new Vector3f(0, -9.81f, 0));

        addGroundPlane();
        createHardcodedRoad();   // <-- Our simple road + walls lives here
    }

    private void addGroundPlane() {
        BoxCollisionShape groundShape =
                new BoxCollisionShape(new Vector3f(100000f, 0.5f, 10000f));
        PhysicsRigidBody ground = new PhysicsRigidBody(groundShape, 0f);
        ground.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        physicsSpace.addCollisionObject(ground);
    }

    /**
     * Creates a simple straight 1 km long road, 6 metres wide,
     * with 3-metre-tall walls on both sides to prevent escaping.
     */
    private void createHardcodedRoad() {

        // Road is from z = 0 to z = 1000
        RoadSegment seg = new RoadSegment(
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 1000)
        );

        activeSegments.add(seg);

        float halfLength = seg.getLength() / 2f;    // 500m
        float halfWidth  = seg.getLaneCount() * seg.getLaneWidth() / 2f;  // 3m (6m road)

        // -------------------- ROAD SURFACE COLLIDER ----------------

        BoxCollisionShape roadShape =
                new BoxCollisionShape(new Vector3f(halfLength, 0.1f, halfWidth));

        PhysicsRigidBody roadBody = new PhysicsRigidBody(roadShape, 0f);

        Vector3f start = seg.getStartPoint();
        Vector3f end   = seg.getEndPoint();

        Vector3f midpoint = start.add(end).mult(0.5f);

        roadBody.setPhysicsLocation(midpoint);

        physicsSpace.addCollisionObject(roadBody);

        // -------------------- WALLS BELOW ----------------

        float wallHeight = 3f;         // 3 metres tall
        float wallThickness = 0.2f;    // ~20 cm thick

        // Walls are as long as the road
        BoxCollisionShape wallShape = new BoxCollisionShape(
                new Vector3f(halfLength, wallHeight / 2f, wallThickness / 2f)
        );

        // LEFT WALL  (-halfWidth = -3m)
        PhysicsRigidBody leftWall = new PhysicsRigidBody(wallShape, 0f);
        leftWall.setPhysicsLocation(
                midpoint.add(0, wallHeight / 2f, -halfWidth)
        );
        physicsSpace.addCollisionObject(leftWall);

        // RIGHT WALL  (+halfWidth = +3m)
        PhysicsRigidBody rightWall = new PhysicsRigidBody(wallShape, 0f);
        rightWall.setPhysicsLocation(
                midpoint.add(0, wallHeight / 2f, +halfWidth)
        );
        physicsSpace.addCollisionObject(rightWall);

    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    public List<RoadSegment> getActiveSegments() {
        return activeSegments;
    }

    public void step(float dt) {
        physicsSpace.update(dt);
    }
}
