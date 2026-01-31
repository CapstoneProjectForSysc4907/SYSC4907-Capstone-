package group7.capstone.technicalsubsystem;


/// This is the contoller you should actually call to do everything. The other classes are technical, this one
/// is simple.
/// Contact Brock Fielder if you wanna change many technical details in the other classes

public class TechnicalSubsystemController {

    private final MapObject world;
    private final CarObject car;
    private final RoadDataHolder roadData = RoadDataHolder.getInstance();

    public TechnicalSubsystemController() {
        this.world = new MapObject();
        this.car = new CarObject("Car_01", world);
    }

    public void getRoadData(RoadSegment road) {
        roadData.getRoadList();
    }

    public void addRoadData(RoadSegment road) {
        roadData.addRoadData(road);
    }

    public void update(float throttle, float brake, float steering, float dt) {
        car.update(throttle, brake, steering, dt);
        world.step(dt);
    }

    public float getSpeedKmh() {return car.getSpeed();}

    public String getPositionString() {return car.getPosition().toString();}


    public String getOrientationString() {return car.getCompassDirection(); }

    public com.jme3.math.Vector3f getPosition() { return car.getPosition();}

    public float getStopDistance(){return car.getStopDistance();}
}
