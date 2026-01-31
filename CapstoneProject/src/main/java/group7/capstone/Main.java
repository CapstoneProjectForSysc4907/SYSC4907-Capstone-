package group7.capstone;

import com.jme3.system.NativeLibraryLoader;
import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.APIStub;
import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.APIController.StreetViewImage;
import group7.capstone.technicalsubsystem.RoadSegment;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        // --- Optional: API demo (wrap so it doesn't kill the physics smoke test) ---
        try {
            GoogleMapsAPIController controller1 = new GoogleMapsAPIController();
            StreetViewImage img = APIStub.getClosestImage("45.53923474756772", "-76.71215350154397");
            // img.saveImageToFile("png", "test.png");
        } catch (Exception e) {
            System.out.println("[WARN] Skipping StreetView stub demo: " + e.getMessage());
        }

        // Bullet native
        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        // --- Create subsystem controller ---
        TechnicalSubsystemController controller = new TechnicalSubsystemController();

        // --- Inject fake "Snap-to-Roads" data ---
        APIResponseDomain fake = fakeSnapResponse_OttawaStraightThenTurn();
        injectFakeRoadFromSnapResponse(controller, fake);

        // --- IMPORTANT: rebuild roads so MapObject uses the injected points ---
        // (rename if your method is rebuildWorldRoads(), rebuildRoadsFromHolder(), etc.)
        controller.rebuildRoads();

        // --- Run your drive test ---
        testDrive(controller);
    }

    private static APIResponseDomain fakeSnapResponse_OttawaStraightThenTurn() {
        APIResponseDomain api = new APIResponseDomain();
        List<APIResponseDomain.SnappedPoint> points = new ArrayList<>();

        double baseLat = 45.4215;
        double baseLon = -75.6972;

        // Segment A: north
        for (int i = 0; i < 25; i++) {
            points.add(makeSnappedPoint(baseLat + i * 0.00005, baseLon, i));
        }

        // Segment B: east
        double turnLat = baseLat + 24 * 0.00005;
        for (int i = 1; i <= 25; i++) {
            points.add(makeSnappedPoint(turnLat, baseLon + i * 0.00005, 25 + i));
        }

        api.setSnappedPoints(points);
        return api;
    }

    private static APIResponseDomain.SnappedPoint makeSnappedPoint(double lat, double lon, int idx) {
        APIResponseDomain.LatLng latLng = new APIResponseDomain.LatLng();
        latLng.setLatitude(lat);
        latLng.setLongitude(lon);

        APIResponseDomain.SnappedPoint sp = new APIResponseDomain.SnappedPoint();
        sp.setLocation(latLng);
        sp.setOriginalIndex(idx);
        sp.setPlaceId("fake_" + idx);

        return sp;
    }

    private static void injectFakeRoadFromSnapResponse(TechnicalSubsystemController controller, APIResponseDomain api) {
        if (api == null || api.getSnappedPoints() == null || api.getSnappedPoints().size() < 2) {
            System.out.println("No snapped points to inject; using hardcoded road fallback inside MapObject.");
            return;
        }

        int count = 0;
        for (APIResponseDomain.SnappedPoint sp : api.getSnappedPoints()) {
            if (sp == null || sp.getLocation() == null) continue;

            double lat = sp.getLocation().getLatitude();
            double lon = sp.getLocation().getLongitude();

            controller.addRoadData(new RoadSegment(lat, lon));
            count++;
        }

        System.out.println("Injected snapped points into RoadDataHolder: " + count);
    }

    public static void testDrive(TechnicalSubsystemController controller) {

        float simTime = 0f;
        float dt = 1f / 60f;

        System.out.println("---- Starting TechnicalSubsystemController Road Test ----");

        while (simTime < 20f) {

            float throttle = 0f;
            float brake    = 0f;
            float steering = 0f;

            if (simTime < 5f) {
                throttle = 1.0f;
            } else if (simTime < 14f) {
                throttle = 1.0f;
                steering = 0.25f; // was 1.0f; smaller steering makes the test less chaotic
            } else {
                brake = 1.0f;
            }

            controller.update(throttle, brake, steering, dt);
            simTime += dt;

            if (((int)(simTime * 10)) % 5 == 0) {
                System.out.println(
                        "t=" + String.format("%.2f", simTime) +
                                "s | Pos=" + controller.getPosition() +
                                " | Speed=" + String.format("%.1f", controller.getSpeedKmh()) +
                                " | Dir=" + controller.getOrientationString()
                );
            }
        }

        System.out.println("---- Road Test Complete ----");
    }
}
