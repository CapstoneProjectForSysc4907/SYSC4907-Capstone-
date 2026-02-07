package group7.capstone;

import com.jme3.system.NativeLibraryLoader;
import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;

public class Main {

    public static void main(String[] args) throws Exception {

        // MUST be FIRST: load Bullet native before PhysicsSpace is created
        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        GoogleMapsAPIController googleApi = new GoogleMapsAPIController();
        TechnicalSubsystemController controller = new TechnicalSubsystemController(googleApi);

        double startLat = 45.4240;
        double startLon = -75.6950;
        int startHeadDeg = 0; // degrees from north

        System.out.println("Requesting initial road...");
        APIResponseDomain initialRoad = googleApi.getStreet(startLat, startLon, startHeadDeg);
        controller.setRouteFromApi(initialRoad);

        System.out.println("Initial road loaded: geoPts=" + controller.getGeoPointCount()
                + " segs=" + controller.getPhysicsSegmentCount());

        float dt = 1f / 60f;
        float simTime = 0f;

        float throttle = 0.85f;
        float brake = 0.0f;
        float steering = 0.0f;

        float lastPrint = 0f;
        float endTime = 30f;

        while (simTime < endTime) {

            steering = (float) Math.sin(simTime * 0.25f) * 0.10f;

            controller.updateAndMaybeRequestMoreRoad(throttle, brake, steering, dt);

            simTime += dt;

            if (simTime - lastPrint >= 0.5f) {
                lastPrint = simTime;

                System.out.println(
                        "t=" + String.format("%.2f", simTime) + "s"
                                + " | Pos=" + controller.getPositionString()
                                + " | Speed=" + String.format("%.1f", controller.getSpeedKmh()) + " km/h"
                                + " | Head=" + controller.getHeadingDegrees() + "°"
                                + " | Dir=" + controller.getOrientationString()
                                + " | onRoad=" + controller.isOnRoad()
                                + " | remaining=" + String.format("%.1f", controller.getRemainingRoadMeters()) + " m"
                                + " | segs=" + controller.getPhysicsSegmentCount()
                                + " | geoPts=" + controller.getGeoPointCount()
                );
            }
        }

        System.out.println("Done.");
    }
}
