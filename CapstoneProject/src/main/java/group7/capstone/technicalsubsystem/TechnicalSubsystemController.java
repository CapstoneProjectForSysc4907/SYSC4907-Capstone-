package group7.capstone.technicalsubsystem;

import com.jme3.math.Vector3f;
import group7.capstone.APIController.APIResponseDomain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TechnicalSubsystemController {

    private final MapObject world;
    private final CarObject car;
    private final RoadPipelineController roadPipeline;

    private List<PhysicsRoadSegment> activeRouteSegments = Collections.emptyList();

    public TechnicalSubsystemController() {
        this.world = new MapObject();
        this.car = new CarObject("Car_01", world);
        this.roadPipeline = new RoadPipelineController(2, 3.7f);
    }

    public void update(float throttle, float brake, float steering, float dt) {
        car.update(throttle, brake, steering, dt);
        world.step(dt);
    }

    public void setRouteFromApi(APIResponseDomain response) {
        //This is where you should send any API reponses don't touch anything else
        roadPipeline.runFromApiResponse(response);
        activeRouteSegments = roadPipeline.getPhysicsSegments();
        car.setRouteSegments(activeRouteSegments);
    }

    public void setRouteFromGeoPoints(List<RoadSegment> geoPoints) {
        roadPipeline.runFromGeoPoints(geoPoints);
        activeRouteSegments = roadPipeline.getPhysicsSegments();
        car.setRouteSegments(activeRouteSegments);
    }

    public List<Double> getCurrentLongAndLat(){
        //If you want to set an image, only touch this, no need to dig through the rest of the code here
        List<Double> output = new ArrayList<>();
        output.add(car.getCurrentSegment().getOriginalSegment().getLatitude());
        output.add(car.getCurrentSegment().getOriginalSegment().getLongitude());
        return output;
    }

    public List<PhysicsRoadSegment> getActiveRouteSegments() { return activeRouteSegments; }

    public float getSpeedKmh() { return car.getSpeed(); }
    public String getPositionString() { return car.getPosition().toString(); }
    public String getOrientationString() { return car.getCompassDirection(); }
    public Vector3f getPosition() { return car.getPosition(); }

    public float getStopDistance() { return car.getStopDistance(); }

    public boolean isOnRoad() { return car.isOnRoad(); }

    public int getGeoPointCount() { return roadPipeline.getGeoPoints().size(); }
    public int getPhysicsSegmentCount() { return roadPipeline.getPhysicsSegments().size(); }
}
