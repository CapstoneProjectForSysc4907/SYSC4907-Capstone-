package group7.capstone;

import com.jme3.system.NativeLibraryLoader;
import com.jme3.math.Vector3f;
import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.technicalsubsystem.RoadSegment;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    public static void main(String[] args) throws IOException {

        // Bullet native
        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        // --- Create subsystem controller ---
        TechnicalSubsystemController controller = new TechnicalSubsystemController();

        // --- Inject a long fake polyline (multiple straights + turns) ---
        APIResponseDomain fake = fakeSnapResponse_LongOttawaPolyline();

        // Print the polyline so we can see what it looks like
        //printPolyline(fake);

        injectFakeRoadFromSnapResponse(controller, fake);

        // IMPORTANT: rebuild roads so MapObject uses the injected points
//        controller.rebuildRoads();

        // Smoke test
        runOffRoadStressSmokeTest(controller);

        // Long test
        runLongPolylineDriveTest(controller);
    }

    /**
     * Long polyline: like "drive north, turn east, turn north, turn west, etc."
     */
    private static APIResponseDomain fakeSnapResponse_LongOttawaPolyline() {
        APIResponseDomain api = new APIResponseDomain();
        List<APIResponseDomain.SnappedPoint> points = new ArrayList<>();

        double lat = 45.4215;
        double lon = -75.6972;

        double step = 0.00005;
        int idx = 0;

        idx = addSegment(points, lat, lon, +step, 0.0, 120, idx); // North
        lat = lastLat(points);
        lon = lastLon(points);

        idx = addSegment(points, lat, lon, 0.0, +step, 140, idx); // East
        lat = lastLat(points);
        lon = lastLon(points);

        idx = addSegment(points, lat, lon, +step, 0.0, 100, idx); // North
        lat = lastLat(points);
        lon = lastLon(points);

        idx = addSegment(points, lat, lon, 0.0, -step, 160, idx); // West
        lat = lastLat(points);
        lon = lastLon(points);

        idx = addSegment(points, lat, lon, +step, 0.0, 110, idx); // North
        lat = lastLat(points);
        lon = lastLon(points);

        idx = addSegment(points, lat, lon, 0.0, +step, 90, idx); // East
        lat = lastLat(points);
        lon = lastLon(points);

        idx = addSegment(points, lat, lon, -step, 0.0, 80, idx); // South
        lat = lastLat(points);
        lon = lastLon(points);

        addSegment(points, lat, lon, 0.0, +step, 120, idx); // East

        api.setSnappedPoints(points);
        return api;
    }

    private static double lastLat(List<APIResponseDomain.SnappedPoint> pts) {
        APIResponseDomain.SnappedPoint last = pts.get(pts.size() - 1);
        return last.getLocation().getLatitude();
    }

    private static double lastLon(List<APIResponseDomain.SnappedPoint> pts) {
        APIResponseDomain.SnappedPoint last = pts.get(pts.size() - 1);
        return last.getLocation().getLongitude();
    }

    private static int addSegment(List<APIResponseDomain.SnappedPoint> points,
                                  double startLat, double startLon,
                                  double dLat, double dLon,
                                  int count, int startIdx) {

        double lat = startLat;
        double lon = startLon;

        if (points.isEmpty()) {
            points.add(makeSnappedPoint(lat, lon, startIdx));
            startIdx++;
        }

        for (int i = 0; i < count; i++) {
            lat += dLat;
            lon += dLon;
            points.add(makeSnappedPoint(lat, lon, startIdx));
            startIdx++;
        }

        return startIdx;
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

    // ===================== Pretty formatting helpers =====================

    private static String fmtV(Vector3f v) {
        if (v == null) return "null";
        return String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    // Polyline printer
    /*private static void printPolyline(APIResponseDomain api) {
        if (api == null || api.getSnappedPoints() == null || api.getSnappedPoints().isEmpty()) {
            System.out.println("[POLYLINE] No snapped points to print.");
            return;
        }

        List<APIResponseDomain.SnappedPoint> pts = api.getSnappedPoints();
        int n = pts.size();

        double minLat = Double.POSITIVE_INFINITY, maxLat = Double.NEGATIVE_INFINITY;
        double minLon = Double.POSITIVE_INFINITY, maxLon = Double.NEGATIVE_INFINITY;

        APIResponseDomain.SnappedPoint first = pts.get(0);
        APIResponseDomain.SnappedPoint last  = pts.get(n - 1);

        for (APIResponseDomain.SnappedPoint sp : pts) {
            if (sp == null || sp.getLocation() == null) continue;
            double lat = sp.getLocation().getLatitude();
            double lon = sp.getLocation().getLongitude();
            if (lat < minLat) minLat = lat;
            if (lat > maxLat) maxLat = lat;
            if (lon < minLon) minLon = lon;
            if (lon > maxLon) maxLon = lon;
        }

        System.out.println("---- POLYLINE DUMP ----");
        System.out.println("[POLYLINE] points=" + n);
        System.out.println("[POLYLINE] bbox lat=[" + minLat + ", " + maxLat + "] lon=[" + minLon + ", " + maxLon + "]");
        if (first != null && first.getLocation() != null) {
            System.out.println("[POLYLINE] start idx=" + first.getOriginalIndex()
                    + " lat=" + first.getLocation().getLatitude()
                    + " lon=" + first.getLocation().getLongitude());
        }
        if (last != null && last.getLocation() != null) {
            System.out.println("[POLYLINE] end   idx=" + last.getOriginalIndex()
                    + " lat=" + last.getLocation().getLatitude()
                    + " lon=" + last.getLocation().getLongitude());
        }

        int sampleEvery = Math.max(1, n / 40);
        System.out.println("[POLYLINE] sample_every=" + sampleEvery);

        for (int i = 0; i < n; i += sampleEvery) {
            APIResponseDomain.SnappedPoint sp = pts.get(i);
            if (sp == null || sp.getLocation() == null) continue;
            System.out.println("[POLYLINE] i=" + i
                    + " idx=" + sp.getOriginalIndex()
                    + " lat=" + sp.getLocation().getLatitude()
                    + " lon=" + sp.getLocation().getLongitude());
        }

        if (last != null && last.getLocation() != null) {
            System.out.println("[POLYLINE] i=" + (n - 1)
                    + " idx=" + last.getOriginalIndex()
                    + " lat=" + last.getLocation().getLatitude()
                    + " lon=" + last.getLocation().getLongitude());
        }

        System.out.println("---- END POLYLINE DUMP ----");
    }*/

    private static void injectFakeRoadFromSnapResponse(TechnicalSubsystemController controller, APIResponseDomain api) {
        if (api == null || api.getSnappedPoints() == null || api.getSnappedPoints().size() < 2) {
            System.out.println("[WARN] No snapped points to inject; using hardcoded road fallback inside MapObject.");
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

    private static void runOffRoadStressSmokeTest(TechnicalSubsystemController controller) {
        float simTime = 0f;
        float dt = 1f / 60f;
        float maxTime = 40f;

        float lastPrint = -999f;
        float stuckTimer = 0f;

        System.out.println("---- Starting OFF-ROAD STRESS SMOKE TEST ----");
        System.out.println("[NOTE] This does NOT prove rail snap-back. It only stresses steering + physics stability.");

        while (simTime < maxTime) {

            float throttle = 0.9f;
            float brake    = 0f;

            float steering;
            if (simTime < 10f) steering = 0.35f;
            else if (simTime < 20f) steering = -0.35f;
            else if (simTime < 30f) steering = 0.45f;
            else steering = -0.45f;

            controller.update(throttle, brake, steering, dt);
            simTime += dt;

            float speed = controller.getSpeedKmh();

            if (throttle > 0.7f && speed < 1.0f) stuckTimer += dt;
            else stuckTimer = 0f;

            if (simTime - lastPrint >= 0.5f) {
                lastPrint = simTime;

                System.out.println(
                        "t=" + String.format("%.2f", simTime) +
                                "| tar poi:" + controller.closepoint() +  " "+
                                "s | Pos=" + fmtV(controller.getPosition()) +
                                " | Speed=" + String.format("%.1f", speed) +
                                " | Dir=" + controller.getOrientationString() +
                                " | thr=" + String.format("%.2f", throttle) +
                                " br=" + String.format("%.2f", brake) +
                                " st=" + String.format("%.2f", steering) +
                                " | Rail: engaged=" + controller.getRailEngaged() +
                                " reason=" + controller.getRailReason() +
                                " dist=" + String.format("%.2f", controller.getRailDistToCenter()) +
                                " yawT=" + String.format("%.2f", controller.getRailYawTorqueMag()) +
                                " latF=" + String.format("%.2f", controller.getRailLateralForceMag()) +
                                " | target=" + fmtV(controller.getRailClosestPoint()) +
                                " seg=[" + fmtV(controller.getRailSegStart()) + " -> " + fmtV(controller.getRailSegEnd()) + "]" +
                                " toCenter=" + fmtV(controller.getRailToCenter()) +
                                " latForce=" + fmtV(controller.getRailAppliedLateralForce()) +
                                " torque=" + fmtV(controller.getRailAppliedTorque())
                );
            }

            if (stuckTimer > 5f) {
                System.out.println("[FAIL] Stuck: throttle high but speed < 1 km/h for >5s at t="
                        + String.format("%.2f", simTime) + " pos=" + fmtV(controller.getPosition()));
                break;
            }
        }

        System.out.println("---- OFF-ROAD STRESS SMOKE TEST COMPLETE ----");
    }

    private static void runLongPolylineDriveTest(TechnicalSubsystemController controller) {

        float simTime = 0f;
        float dt = 1f / 60f;
        float maxTime = 180f;

        float lastPrint = -999f;
        float stuckTimer = 0f;

        System.out.println("---- Starting LONG Polyline Road Test ----");

        controller.debugSpeed();

        while (simTime < maxTime) {

            float throttle = 0f;
            float brake    = 0f;
            float steering = 0f;

            if (simTime < 15f) {
                throttle = 1.0f;
                steering = 0.0f;
            } else if (simTime < 45f) {
                throttle = 1.0f;
                steering = 0.18f;
            } else if (simTime < 60f) {
                throttle = 1.0f;
                steering = 0.0f;
            } else if (simTime < 90f) {
                throttle = 0.9f;
                steering = -0.18f;
            } else if (simTime < 110f) {
                throttle = 0.9f;
                steering = 0.0f;
            } else if (simTime < 140f) {
                throttle = 0.85f;
                steering = 0.22f;
            } else if (simTime < 155f) {
                throttle = 0.0f;
                steering = 0.0f;
            } else {
                brake = 1.0f;
                steering = 0.0f;
            }

            controller.update(throttle, brake, steering, dt);
            simTime += dt;

            float speed = controller.getSpeedKmh();

            if (throttle > 0.7f && speed < 1.0f) stuckTimer += dt;
            else stuckTimer = 0f;

            if (simTime - lastPrint >= 0.5f) {
                lastPrint = simTime;

                if (simTime - lastPrint >= 0.5f) {
                    lastPrint = simTime;

                    System.out.println(
                            "t=" + String.format("%.2f", simTime) +
                                    "s | Pos=" + fmtV(controller.getPosition()) +
                                    " | Speed=" + String.format("%.1f", speed) +
                                    " | Dir=" + controller.getOrientationString() +
                                    " | thr=" + String.format("%.2f", throttle) +
                                    " br=" + String.format("%.2f", brake) +
                                    " st=" + String.format("%.2f", steering) +
                                    " | Rail: engaged=" + controller.getRailEngaged() +
                                    " reason=" + controller.getRailReason() +
                                    " dist=" + String.format("%.2f", controller.getRailDistToCenter()) +
                                    " segIdx=" + controller.getRailSegIndex() +
                                    " t=" + String.format("%.3f", controller.getRailSegT()) +
                                    " yawT=" + String.format("%.2f", controller.getRailYawTorqueMag()) +
                                    " latF=" + String.format("%.2f", controller.getRailLateralForceMag()) +
                                    " | target=" + fmtV(controller.getRailClosestPoint()) +
                                    " seg=[" + fmtV(controller.getRailSegStart()) + " -> " + fmtV(controller.getRailSegEnd()) + "]" +
                                    " toCenter=" + fmtV(controller.getRailToCenter()) +
                                    " latForce=" + fmtV(controller.getRailAppliedLateralForce()) +
                                    " torque=" + fmtV(controller.getRailAppliedTorque())
                    );
                }

            }

            if (stuckTimer > 5f) {
                System.out.println("[FAIL] Stuck: throttle high but speed < 1 km/h for >5s at t="
                        + String.format("%.2f", simTime) + " pos=" + fmtV(controller.getPosition()));
                break;
            }
        }

        System.out.println("---- LONG Polyline Road Test Complete ---- ");
    }
}
