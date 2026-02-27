package group7.capstone.technicalsubsystem;

import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

import java.util.List;

/// DO NOT CALL THIS CLASS FROM OUTSIDE THE SUBSYSTEM
public class CarObject {

    private final String id;
    private final float massOfCar;
    private final VehiclePhysicsSystem physics;
    private final MapObject world;
    private final PhysicsRigidBody body;
    private final VehicleConfig config = VehicleConfig.getInstance();

    // Only do the initial "snap to road" once, when the first route is loaded.
    private boolean routeInitialized = false;

    public CarObject(String id, MapObject world) {
        this.id = id;
        this.massOfCar = config.getMass();
        this.world = world;

        BoxCollisionShape carShape = new BoxCollisionShape(new Vector3f(1f, 0.5f, 2f));
        body = new PhysicsRigidBody(carShape, massOfCar);

        body.setPhysicsLocation(new Vector3f(0, 1f, 0));
        body.setFriction(0.2f);

        world.getPhysicsSpace().addCollisionObject(body);
        this.physics = new VehiclePhysicsSystem(body);
    }

    public void setRouteSegments(List<PhysicsRoadSegment> segments) {
        physics.setRouteSegments(segments);

        // When a route is first loaded, start the car on the first segment.
        if (!routeInitialized && segments != null && !segments.isEmpty()) {
            PhysicsRoadSegment first = segments.get(0);
            Vector3f start = first.getStartPoint();
            Vector3f dir = first.getEndPoint().subtract(first.getStartPoint());
            dir.y = 0f;
            if (dir.lengthSquared() < 1e-6f) dir.set(0, 0, 1);
            dir.normalizeLocal();

            start.y = 0.50f;

            physics.hardResetTo(start, dir);
            routeInitialized = true;
        }
    }

    public void update(float throttle, float brake, float steering, float dt) {
        // IMPORTANT: rail computed ONCE per frame
        physics.updateRailState(dt);

        physics.steer(steering);
        physics.changeSpeed(throttle, brake, dt);
        physics.updateSteering(dt);

        if (physics.shouldTeleportBack(dt)) {
            physics.teleportToNearestRoad();
        }
    }

    public PhysicsRoadSegment getCurrentSegment() {
        return physics.getCurrentSegment();
    }

    public Vector3f getPosition() {
        return physics.getPosition();
    }

    public float getSpeed() {
        return physics.getSpeed();
    }

    public float getStopDistance() {
        return physics.calculateStoppingDistance(getSpeed());
    }

    public boolean isOnRoad() {
        return physics.isOnRoad();
    }

    public float getRemainingRoadMeters() {
        return physics.getRemainingRoadMeters();
    }

    public String getId() {
        return id;
    }

    /**
     * GOOGLE API wants heading in DEGREES from North.
     * We'll compute yaw from body forward vector:
     *  - forward = UNIT_Z rotated
     *  - atan2(forward.x, forward.z) gives 0 when pointing north (z+)
     *  - degrees in [0, 360)
     */
    public int getHeadingDegrees() {
        Vector3f forward = body.getPhysicsRotation().mult(Vector3f.UNIT_Z);

        float angle = (float) Math.atan2(forward.x, forward.z);
        float degrees = (float) Math.toDegrees(angle);

        if (degrees < 0f) degrees += 360f;

        // Round to int; API signature is int head
        return Math.round(degrees);
    }

    public String getCompassDirection() {
        int degrees = getHeadingDegrees();

        if (degrees >= 337.5 || degrees < 22.5) return "N " + degrees + "° ";
        if (degrees < 67.5) return "NE " + degrees + "° ";
        if (degrees < 112.5) return "E " + degrees + "° ";
        if (degrees < 157.5) return "SE " + degrees + "° ";
        if (degrees < 202.5) return "S " + degrees + "° ";
        if (degrees < 247.5) return "SW " + degrees + "° ";
        if (degrees < 292.5) return "W " + degrees + "° ";
        return "NW " + degrees + "° ";
    }
}
