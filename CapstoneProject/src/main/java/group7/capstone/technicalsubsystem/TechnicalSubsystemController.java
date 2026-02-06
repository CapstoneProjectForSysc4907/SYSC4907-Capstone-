package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;

import java.util.ArrayList;
import java.util.List;

/// This is the controller you should actually call to do everything. The other classes are technical, this one
/// is simple.
/// Contact Brock Fielder if you wanna change many technical details in the other classes
public class TechnicalSubsystemController {

    private final MapObject world;
    private final CarObject car;
    private final RoadDataHolder roadData = RoadDataHolder.getInstance();

    // NEW: rail assist + toggle
    private final RailAssist railAssist = new RailAssist();
    private boolean railAssistEnabled = true; // default ON for now

    public TechnicalSubsystemController() {
        this.world = new MapObject();
        this.car = new CarObject("Car_01", world);
    }

    public ArrayList<RoadSegment> getRoadData() {
        return roadData.getRoadList();
    }

    public void addRoadData(RoadSegment road) {
        roadData.addRoadData(road);
    }

    public void rebuildRoads() {
        world.rebuildRoadsFromHolder();
    }

    // NEW: allow turning rail assist on/off (optional, but tiny + useful)
    public void setRailAssistEnabled(boolean enabled) {
        this.railAssistEnabled = enabled;
    }

    public void debugSpeed(){
        car.debugSetSpeed();
    }

    public boolean isRailAssistEnabled() {
        return railAssistEnabled;
    }

    public void update(float throttle, float brake, float steering, float dt) {

        world.rebuildRoadsFromHolderIfChanged();
        // Existing car update (keeps your current throttle/brake/steering behaviour)
        car.update(throttle, brake, steering, dt);

        // NEW: apply rail assist BEFORE stepping physics so the forces affect this tick
        if (railAssistEnabled) {
            railAssist.apply(car.getRigidBody(), world.getActiveSegments(), dt);
        }

        // Step Bullet
        world.step(dt);
    }

    public List<Vector3f> getForces() {return car.getForces();}

    public float getSpeedKmh() { return car.getSpeed(); }

    public String getPositionString() { return car.getPosition().toString(); }

    public String getOrientationString() { return car.getCompassDirection(); }

    public com.jme3.math.Vector3f getPosition() { return car.getPosition(); }

    public float getStopDistance() { return car.getStopDistance(); }
}