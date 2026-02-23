package group7.capstone;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.jme3.system.NativeLibraryLoader;
import group7.capstone.APIController.*;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;
import group7.capstone.caching.RoadApiCacheManager;
import group7.capstone.technicalsubsystem.InputHandler;
import group7.capstone.visuals.ImageLoader;
import group7.capstone.visuals.GUI.SimulatorFrame;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;

public class Main {

    public static void main(String[] args) throws Exception {

        // MUST be FIRST: load Bullet native before PhysicsSpace is created
        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        GoogleMapsAPIController googleApi = new GoogleMapsAPIController();
        RoadApiCacheManager roadCache = new RoadApiCacheManager(googleApi);
        TechnicalSubsystemController controller = new TechnicalSubsystemController(googleApi, roadCache);

        double startLat = 45.3170722;
        double startLon = -76.0796364;
        int startHeadDeg = 102; // degrees from north

        System.out.println("Requesting initial road...");
        APIResponseDomain initialRoad = roadCache.getStreet(startLat, startLon, startHeadDeg);
        System.out.println(roadCache.getStats());
        controller.setRouteFromApi(initialRoad);

        System.out.println("Initial road loaded: geoPts=" + controller.getGeoPointCount()
                + " segs=" + controller.getPhysicsSegmentCount());

        // --- GUI setup (Swing) ---
        SimulatorFrame[] frameRef = new SimulatorFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            SimulatorFrame frame = new SimulatorFrame();
            frame.setVisible(true);

            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    InputHandler.requestExit();
                }
            });

            // quick placeholder to avoid blank panel
            frame.setStreetViewImage(new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB));
            frame.setFooterStatus("Loading...");
            frameRef[0] = frame;
        });

        SimulatorFrame frame = frameRef[0];
        ImageLoader imageLoader = new ImageLoader(googleApi);

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

        // --- GUI update cadence (don’t spam EDT) ---
        float hudTick = 0f;
        final float HUD_DT = 1f / 10f; // 10 Hz

        float imgTick = 0f;
        final float IMG_DT = 5f; // 1 Hz max

        // last requested image key
        double lastImgLat = Double.NaN;
        double lastImgLon = Double.NaN;
        int lastImgHeading = -1;

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

            // ---- GUI updates ----
            hudTick += dt;
            imgTick += dt;

            if (hudTick >= HUD_DT && frame != null) {
                hudTick = 0f;

                double lat = controller.getCurrentLatitude();
                double lon = controller.getCurrentLongitude();
                int head = controller.getHeadingDegrees();
                double kmh = controller.getSpeedKmh();

                String status;
                if (!controller.isOnRoad()) {
                    status = "OFF_ROAD";
                } else {
                    float remaining = controller.getRemainingRoadMeters();
                    status = (remaining < 60f) ? "NEED_MORE_ROAD" : "OK";
                }

                String footer = String.format(
                        "segs=%d | remaining=%.1fm | imgCache=%d | loading=%d",
                        controller.getPhysicsSegmentCount(),
                        controller.getRemainingRoadMeters(),
                        imageLoader.getCacheSize(),
                        imageLoader.getLoadingCount()
                );

                SwingUtilities.invokeLater(() -> {
                    frame.getHud().setSpeed(kmh);
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        frame.getHud().setLatLng(lat, lon);
                    }
                    frame.getHud().setHeading(head);
                    frame.getHud().setStatus(status);
                    frame.setFooterStatus(footer);
                });
            }

            // Request Street View at most once per second, and only if we have geo.
            if (imgTick >= IMG_DT && frame != null) {
                imgTick = 0f;

                double lat = controller.getCurrentLatitude();
                double lon = controller.getCurrentLongitude();
                int head = controller.getHeadingDegrees();

                boolean haveGeo = !Double.isNaN(lat) && !Double.isNaN(lon);

                // avoid spamming the same request
                boolean changedEnough =
                        !haveGeo ? false :
                        Double.isNaN(lastImgLat)
                                || Math.abs(lat - lastImgLat) > 0.0001
                                || Math.abs(lon - lastImgLon) > 0.0001
                                || Math.abs(head - lastImgHeading) >= 10;

                if (changedEnough) {
                    lastImgLat = lat;
                    lastImgLon = lon;
                    lastImgHeading = head;

                    SwingUtilities.invokeLater(() -> frame.getHud().setStatus("LOADING"));

                    imageLoader.loadImageAsync(lat, lon, head, img -> {
                        SwingUtilities.invokeLater(() -> {
                            frame.setStreetViewImage(img);
                            // don’t overwrite OFF_ROAD if that’s currently true
                            if (controller.isOnRoad()) {
                                frame.getHud().setStatus("OK");
                            }
                        });
                    });
                }
            }

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
        try { imageLoader.shutdown(); } catch (Exception ignored) {}

        System.out.println("Done.");
    }
}