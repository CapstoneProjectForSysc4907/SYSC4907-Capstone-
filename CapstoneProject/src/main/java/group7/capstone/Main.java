package group7.capstone;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.jme3.system.NativeLibraryLoader;
import group7.capstone.APIController.*;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;
import group7.capstone.caching.RoadApiCacheManager;
import group7.capstone.technicalsubsystem.InputHandler;

public class Main {

    public static void main(String[] args) throws Exception {

        // MUST be FIRST: load Bullet native before PhysicsSpace is created
        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        GoogleMapsAPIController googleApi = new GoogleMapsAPIController();
        RoadApiCacheManager roadCache = new RoadApiCacheManager(googleApi);
        TechnicalSubsystemController controller = new TechnicalSubsystemController(googleApi, roadCache);

        double startLat = 45.4240;
        double startLon = -75.6950;
        int startHeadDeg = 0; // degrees from north

        System.out.println("Requesting initial road...");
        APIResponseDomain initialRoad = roadCache.getStreet(startLat, startLon, startHeadDeg);
        System.out.println(roadCache.getStats());
        controller.setRouteFromApi(initialRoad);

        System.out.println("Initial road loaded: geoPts=" + controller.getGeoPointCount()
                + " segs=" + controller.getPhysicsSegmentCount());

        // --- Fixed-step timing (target 60 Hz) ---
        final float dt = 1f / 60f;
        long frameNanos = (long) (dt * 1_000_000_000L);
        long nextTick = System.nanoTime();

        float simTime = 0f;

        // Current controls (updated every frame from InputHandler)
        float throttle = 0.0f;
        float brake = 0.0f;
        float steering = 0.0f;

        // Tuning knobs
        final float THROTTLE_ON = 0.85f;
        final float BRAKE_ON = 0.80f;
        final float STEER_MAG = 0.35f;

        float lastPrint = 0f;
        float endTime = 30f;

        // Initializes key reading
        try {
            GlobalScreen.registerNativeHook();
        } catch (NativeHookException ex) {
            System.err.println("There was a problem registering the native hook.");
            System.err.println(ex.getMessage());
            System.exit(1);
        }
        GlobalScreen.addNativeKeyListener(new InputHandler());

        while (simTime < endTime && !InputHandler.isExitRequested()) {

            // ---- Read input state each frame ----
            throttle = InputHandler.isForward() ? THROTTLE_ON : 0.0f;
            brake    = InputHandler.isBrake()   ? BRAKE_ON    : 0.0f;

            // Optional policy: if braking, cut throttle
            if (brake > 0f) throttle = 0f;

            steering = 0.0f;
            if (InputHandler.isLeft())  steering -= STEER_MAG;
            if (InputHandler.isRight()) steering += STEER_MAG;

            // ---- Step sim ----
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

            // ---- Frame pacing ----
            nextTick += frameNanos;
            long sleep = nextTick - System.nanoTime();
            if (sleep > 0) {
                // sleep(ms, ns)
                Thread.sleep(sleep / 1_000_000L, (int) (sleep % 1_000_000L));
            } else {
                // If we're behind, reset so we don't spiral into huge negative sleep
                nextTick = System.nanoTime();
            }
        }

        // Best-effort cleanup (in case ESC wasn’t used)
        try { GlobalScreen.unregisterNativeHook(); } catch (Exception ignored) {}

        System.out.println("Done.");
    }
}