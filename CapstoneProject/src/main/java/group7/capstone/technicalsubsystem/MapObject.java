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

    // Engine-usable segments (Vector3f start/end + lane info)
    private final List<PhysicsRoadSegment> activeSegments = new ArrayList<>();

    // Pulls raw geo points from the singleton
    private final RoadDataHolder roadData = RoadDataHolder.getInstance();

    public MapObject() {
        physicsSpace = new PhysicsSpace(PhysicsSpace.BroadphaseType.DBVT);
        physicsSpace.setGravity(new Vector3f(0, -9.81f, 0));

        addGroundPlane();

        // Build from real data if present; otherwise fall back to hardcoded demo road
        if (!tryBuildRoadFromRoadData()) {
            createHardcodedRoad();
        }
    }

    private void addGroundPlane() {
        BoxCollisionShape groundShape =
                new BoxCollisionShape(new Vector3f(100000f, 0.5f, 10000f));
        PhysicsRigidBody ground = new PhysicsRigidBody(groundShape, 0f);
        ground.setPhysicsLocation(new Vector3f(0, -0.5f, 0));
        physicsSpace.addCollisionObject(ground);
    }

    /**
     * Converts RoadDataHolder's List<RoadSegment> (lat/lon points)
     * into List<PhysicsRoadSegment> and builds colliders for each segment.
     *
     * @return true if it built at least one road segment, false otherwise.
     */
    private boolean tryBuildRoadFromRoadData() {
        List<RoadSegment> geoPoints = roadData.getRoadList();
        if (geoPoints == null || geoPoints.size() < 2) {
            return false;
        }

        // Defaults for now; you can later derive from API metadata
        int defaultLaneCount = 2;
        float defaultLaneWidthMeters = 3.0f;

        RoadSegmentConverter converter =
                RoadSegmentConverter.fromFirstPoint(geoPoints, defaultLaneCount, defaultLaneWidthMeters);

        List<PhysicsRoadSegment> segments = converter.toPhysicsSegments(geoPoints);
        if (segments.isEmpty()) {
            return false;
        }

        for (PhysicsRoadSegment seg : segments) {
            activeSegments.add(seg);
            buildRoadColliderAndWalls(seg);
        }

        return true;
    }

    /**
     * Fallback demo: Creates a simple straight 1 km long road, 6 metres wide,
     * with 3-metre-tall walls on both sides to prevent escaping.
     */
    private void createHardcodedRoad() {

        PhysicsRoadSegment seg = new PhysicsRoadSegment(
                new Vector3f(0, 0, 0),
                new Vector3f(0, 0, 1000),
                2,
                3.0f
        );

        activeSegments.add(seg);
        buildRoadColliderAndWalls(seg);
    }

    /**
     * Builds:
     * - A road surface collider (box)
     * - Two wall colliders (left/right) along the road
     *
     * NOTE: This matches your current approach: axis-aligned boxes.
     * For curved roads, you'll get best results by building many short segments
     * (which we are doing by splitting the polyline).
     */
    private void buildRoadColliderAndWalls(PhysicsRoadSegment seg) {

        float halfLength = seg.getLength() / 2f;
        float halfWidth = (seg.getLaneCount() * seg.getLaneWidth()) / 2f;

        Vector3f start = seg.getStartPoint();
        Vector3f end   = seg.getEndPoint();
        Vector3f midpoint = start.add(end).mult(0.5f);

        // -------------------- ROAD SURFACE COLLIDER ----------------
        BoxCollisionShape roadShape =
                new BoxCollisionShape(new Vector3f(halfLength, 0.1f, halfWidth));

        PhysicsRigidBody roadBody = new PhysicsRigidBody(roadShape, 0f);
        roadBody.setPhysicsLocation(midpoint);
        physicsSpace.addCollisionObject(roadBody);

        // -------------------- WALLS ----------------
        float wallHeight = 3f;         // 3 metres tall
        float wallThickness = 0.2f;    // ~20 cm thick

        BoxCollisionShape wallShape = new BoxCollisionShape(
                new Vector3f(halfLength, wallHeight / 2f, wallThickness / 2f)
        );

        // IMPORTANT:
        // These walls assume the road is "along Z" and width is "along X or Z"
        // In your hardcoded road, halfWidth is Z thickness. In the geo conversion,
        // local coords are X=east, Z=north. For a perfect wall placement you’d
        // offset perpendicular to the segment direction. This is the simple version
        // matching your existing axis-aligned demo.

        // LEFT WALL  (-halfWidth)
        PhysicsRigidBody leftWall = new PhysicsRigidBody(wallShape, 0f);
        leftWall.setPhysicsLocation(midpoint.add(0, wallHeight / 2f, -halfWidth));
        physicsSpace.addCollisionObject(leftWall);

        // RIGHT WALL  (+halfWidth)
        PhysicsRigidBody rightWall = new PhysicsRigidBody(wallShape, 0f);
        rightWall.setPhysicsLocation(midpoint.add(0, wallHeight / 2f, +halfWidth));
        physicsSpace.addCollisionObject(rightWall);
    }

    public PhysicsSpace getPhysicsSpace() {
        return physicsSpace;
    }

    public List<PhysicsRoadSegment> getActiveSegments() {
        return activeSegments;
    }

    public void step(float dt) {
        physicsSpace.update(dt);
    }
}
