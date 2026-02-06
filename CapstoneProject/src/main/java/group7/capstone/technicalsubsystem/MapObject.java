package group7.capstone.technicalsubsystem;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

/// DO NOT CALL THIS CLASS FROM OUTSIDE THE SUBSYSTEM
public class MapObject {

    private final PhysicsSpace physicsSpace;

    private final List<PhysicsRoadSegment> activeSegments = new ArrayList<>();
    private final RoadDataHolder roadData = RoadDataHolder.getInstance();

    // NEW: track what we add so we can remove it later
    private final List<PhysicsRigidBody> roadAndWallBodies = new ArrayList<>();
    private int lastBuiltRoadVersion = -1;

    // Keep ground separate so we never remove it
    private PhysicsRigidBody groundBody;

    public MapObject() {
        physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        physicsSpace.setGravity(new Vector3f(0, -9.81f, 0));

        addGroundPlane();

    }

    private void addGroundPlane() {
        BoxCollisionShape groundShape = new BoxCollisionShape(new Vector3f(100000f, 0.5f, 10000f));
        groundBody = new PhysicsRigidBody(groundShape, 0f);
        groundBody.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        physicsSpace.addCollisionObject(groundBody);
    }

    private void clearRoadAndWallBodies() {
        for (PhysicsRigidBody b : roadAndWallBodies) {
            physicsSpace.removeCollisionObject(b);
        }
        roadAndWallBodies.clear();
    }

    private boolean tryBuildRoadFromRoadData() {
        List<RoadSegment> geoPoints = roadData.getRoadList();
        if (geoPoints == null || geoPoints.size() < 2) return false;

        int defaultLaneCount = 2;
        float defaultLaneWidthMeters = 3.0f;

        RoadSegmentConverter converter =
                RoadSegmentConverter.fromFirstPoint(geoPoints, defaultLaneCount, defaultLaneWidthMeters);

        List<PhysicsRoadSegment> segments = converter.toPhysicsSegments(geoPoints);
        if (segments.isEmpty()) return false;

        for (PhysicsRoadSegment seg : segments) {
            activeSegments.add(seg);
            buildRoadColliderAndWalls(seg);
        }
        return true;
    }

    public void rebuildRoadsFromHolderIfChanged() {
        int v = roadData.getVersion();
        if (v == lastBuiltRoadVersion) return; // no change => no rebuild

        lastBuiltRoadVersion = v;
        rebuildRoadsFromHolder(); // your existing method
    }



    private void buildRoadColliderAndWalls(PhysicsRoadSegment seg) {

        Vector3f start = seg.getStartPoint();
        Vector3f end   = seg.getEndPoint();

        Vector3f dir = end.subtract(start);
        float length = dir.length();
        if (length < 0.05f) return;

        Vector3f forward = dir.normalize();
        Vector3f right   = new Vector3f(forward.z, 0, -forward.x).normalize();

        Vector3f midpoint = start.add(end).mult(0.5f);

        float halfLength = length / 2f;
        float halfWidth  = (seg.getLaneCount() * seg.getLaneWidth()) / 2f;

        float yaw = FastMath.atan2(forward.x, forward.z);
        Quaternion rot = new Quaternion().fromAngleAxis(yaw, Vector3f.UNIT_Y);

        // ROAD SURFACE
        BoxCollisionShape roadShape = new BoxCollisionShape(new Vector3f(halfWidth, 0.1f, halfLength));
        PhysicsRigidBody roadBody = new PhysicsRigidBody(roadShape, 0f);
        roadBody.setPhysicsLocation(midpoint.clone());
        roadBody.setPhysicsRotation(rot);
        physicsSpace.addCollisionObject(roadBody);
        roadAndWallBodies.add(roadBody);


    }

    public void rebuildRoadsFromHolder() {
        // remove old colliders first
        clearRoadAndWallBodies();

        // clear old segments
        activeSegments.clear();

        // build new road from RoadDataHolder's polyline
        boolean built = tryBuildRoadFromRoadData();

        // (optional) if nothing to build, just leave the world with ground only
        if (!built) {
            // no-op: activeSegments is already empty, colliders already cleared
        }
    }

    public PhysicsSpace getPhysicsSpace() { return physicsSpace; }

    public List<PhysicsRoadSegment> getActiveSegments() { return activeSegments; }

    public void step(float dt) { physicsSpace.update(dt); }
}
