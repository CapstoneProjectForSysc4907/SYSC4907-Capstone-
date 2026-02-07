package group7.capstone;

import com.jme3.system.NativeLibraryLoader;
import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        TechnicalSubsystemController controller = new TechnicalSubsystemController();

        APIResponseDomain fake = fakeSnapResponse_OttawaStraightThenTurn();
        controller.setRouteFromApi(fake);

        testDriveRail(controller);
    }

    private static APIResponseDomain fakeSnapResponse_OttawaStraightThenTurn() {
        APIResponseDomain api = new APIResponseDomain();
        List<APIResponseDomain.SnappedPoint> points = new ArrayList<>();

        double baseLat = 45.4215;
        double baseLon = -75.6972;

        for (int i = 0; i < 30; i++) {
            points.add(makeSnappedPoint(baseLat + i * 0.00005, baseLon, i));
        }

        double turnLat = baseLat + 29 * 0.00005;
        for (int i = 1; i <= 30; i++) {
            points.add(makeSnappedPoint(turnLat, baseLon + i * 0.00005, 30 + i));
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

    public static void testDriveRail(TechnicalSubsystemController controller) {

        float simTime = 0f;
        float dt = 1f / 60f;

        System.out.println("---- Starting Rail Follow Test ----");

        while (simTime < 22f) {

            float throttle = 0f;
            float brake    = 0f;
            float steering = 0f;

            if (simTime < 7f) {
                throttle = 1.0f;
                steering = 0.0f;
            } else if (simTime < 12f) {
                throttle = 1.0f;
                steering = 1.0f;
            } else if (simTime < 17f) {
                throttle = 1.0f;
                steering = 0.0f;
            } else {
                brake = 1.0f;
            }

            controller.update(throttle, brake, steering, dt);
            simTime += dt;

            // Print ~every 0.5s (same cadence you already had)
            if (((int)(simTime * 10)) % 5 == 0) {

                // These 3 are the "rail prints" you wanted:
               // float cross = safe(controller::getCurrentCrossTrackMeters, 0f);
               // float tol   = safe(controller::getCurrentToleranceMeters, Float.POSITIVE_INFINITY);
               // int segIdx  = safeInt(controller::getCurrentSegIndex, -1);

                System.out.println(
                        "t=" + String.format("%.2f", simTime) +
                                "s | Pos=" + controller.getPosition() +
                                " | Speed=" + String.format("%.1f", controller.getSpeedKmh()) +
                                " | Dir=" + controller.getOrientationString() +
                                " | OnRoad=" + controller.isOnRoad() +
                                " | Segments=" + controller.getPhysicsSegmentCount() //+
                        //.// " | SegIdx=" + segIdx +
                                //" | Cross=" + String.format("%.3f", cross) +
                                //"m | Tol=" + String.format("%.3f", tol) + "m"
                );
            }
        }

        System.out.println("---- Rail Follow Test Complete ----");
    }

    // -----------------------
    // Tiny helpers so Main compiles even if you temporarily don't have the getters wired.
    // (If you DO have the getters, these helpers still work fine.)
    // -----------------------
    private interface FloatGetter { float get(); }
    private interface IntGetter { int get(); }

    private static float safe(FloatGetter g, float fallback) {
        try { return g.get(); } catch (Throwable t) { return fallback; }
    }

    private static int safeInt(IntGetter g, int fallback) {
        try { return g.get(); } catch (Throwable t) { return fallback; }
    }
}
