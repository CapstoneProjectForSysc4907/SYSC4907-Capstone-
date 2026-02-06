
package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.List;

/// This is the controller you should actually call to do everything. The other classes are technical, this one
/// is simple.
/// Contact Brock Fielder if you wanna change many technical details in the other classes
public class TechnicalSubsystemController {

    private final MapObject world;
    private final CarObject car;
    private final RoadDataHolder roadData = RoadDataHolder.getInstance();

    // rail assist + toggle
    private final RailAssist railAssist = new RailAssist();
    private boolean railAssistEnabled = true; // default ON for now

    // latest debug snapshot from RailAssist (or null if none)
    private RailAssist.DebugSnapshot latestRailDebug;

    public TechnicalSubsystemController() {
        this.world = new MapObject();
        this.car = new CarObject("Car_01", world);
    }

    // ===== Road data =====
    public List<RoadSegment> getRoadData() {
        return roadData.getRoadListSnapshot();
    }

    public void addRoadData(RoadSegment road) {
        roadData.addRoadData(road);
    }

    /*public void rebuildRoads() {
        world.rebuildRoadsFromHolder();
    }*/

    // ===== Rail assist toggle =====
    public void setRailAssistEnabled(boolean enabled) {
        this.railAssistEnabled = enabled;
        if (!enabled) {
            railAssist.clearLastDebug();
            latestRailDebug = null;
        }
    }

    public boolean isRailAssistEnabled() {
        return railAssistEnabled;
    }

    public void debugSpeed() {
        car.debugSetSpeed();
    }

    public void update(float throttle, float brake, float steering, float dt) {

        //world.rebuildRoadsFromHolderIfChanged();

        // Existing car update (keeps your current throttle/brake/steering behaviour)
        car.update(throttle, brake, steering, dt);

        // Apply rail assist BEFORE stepping physics so the forces affect this tick
        if (railAssistEnabled) {
            //railAssist.apply( car.getRigidBody(), world.getActiveSegments(), dt);
        } else {
            //.clearLastDebug();
        }

        // capture the latest snapshot for flat getters
        captureRailAssistDebug();

        // Step Bullet
        world.step(dt);
    }

    private void captureRailAssistDebug() {
        latestRailDebug = railAssist.hasLastDebug() ? railAssist.getLastDebug() : null;
    }

    // ===================== RailAssist Debug: flat getters =====================

    public boolean hasRailAssistDebug() {
        return latestRailDebug != null;
    }

    public float getRailDt() { return latestRailDebug == null ? 0f : latestRailDebug.getDt(); }
    public int getRailSegsSize() { return latestRailDebug == null ? 0 : latestRailDebug.getSegsSize(); }
    public boolean getRailEngaged() { return latestRailDebug != null && latestRailDebug.isEngaged(); }
    public RailAssist.DebugSnapshot.Reason getRailReason() { return latestRailDebug == null ? null : latestRailDebug.getReason(); }

    public Vector3f getRailLocation() { return latestRailDebug == null ? null : latestRailDebug.getLocation(); }
    public Vector3f getRailVelocity() { return latestRailDebug == null ? null : latestRailDebug.getVelocity(); }
    public float getRailSpeed() { return latestRailDebug == null ? 0f : latestRailDebug.getSpeed(); }

    public Vector3f getRailClosestPoint() { return latestRailDebug == null ? null : latestRailDebug.getClosestPoint(); }
    public float getRailDistToCenter() { return latestRailDebug == null ? 0f : latestRailDebug.getDistToCenter(); }
    public Vector3f getRailSegStart() { return latestRailDebug == null ? null : latestRailDebug.getSegStart(); }
    public Vector3f getRailSegEnd() { return latestRailDebug == null ? null : latestRailDebug.getSegEnd(); }

    public Vector3f getRailDesiredForward() { return latestRailDebug == null ? null : latestRailDebug.getDesiredForward(); }
    public Vector3f getRailCurrentForward() { return latestRailDebug == null ? null : latestRailDebug.getCurrentForward(); }
    public Vector3f getRailLateralDir() { return latestRailDebug == null ? null : latestRailDebug.getLateralDir(); }
    public Vector3f getRailToCenter() { return latestRailDebug == null ? null : latestRailDebug.getToCenter(); }

    public float getRailLateralError() { return latestRailDebug == null ? 0f : latestRailDebug.getLateralError(); }
    public float getRailLateralVel() { return latestRailDebug == null ? 0f : latestRailDebug.getLateralVel(); }
    public float getRailLateralForceMag() { return latestRailDebug == null ? 0f : latestRailDebug.getLateralForceMag(); }
    public Vector3f getRailAppliedLateralForce() { return latestRailDebug == null ? null : latestRailDebug.getAppliedLateralForce(); }

    public float getRailCrossY() { return latestRailDebug == null ? 0f : latestRailDebug.getCrossY(); }
    public float getRailYawError() { return latestRailDebug == null ? 0f : latestRailDebug.getYawError(); }
    public float getRailAngVelY() { return latestRailDebug == null ? 0f : latestRailDebug.getAngVelY(); }
    public float getRailYawTorqueMag() { return latestRailDebug == null ? 0f : latestRailDebug.getYawTorqueMag(); }
    public Vector3f getRailAppliedTorque() { return latestRailDebug == null ? null : latestRailDebug.getAppliedTorque(); }

    public int getRailSegIndex() {
        return latestRailDebug == null ? -1 : latestRailDebug.getSegIndex();
    }

    public Vector3f closepoint (){
        return railAssist.getPoint();
    }
    public float getRailSegT() {
        return latestRailDebug == null ? -1f : latestRailDebug.getSegT();
    }

    // ===== Existing getters =====
    public List<Vector3f> getForces() { return car.getForces(); }
    public float getSpeedKmh() { return car.getSpeed(); }
    public String getPositionString() { return car.getPosition().toString(); }
    public String getOrientationString() { return car.getCompassDirection(); }
    public Vector3f getPosition() { return car.getPosition(); }
    public float getStopDistance() { return car.getStopDistance(); }
}
