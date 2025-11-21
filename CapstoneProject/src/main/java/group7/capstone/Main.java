package group7.capstone;

import com.jme3.system.NativeLibraryLoader;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;

public class Main {

    public static void main(String[] args) {

        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        TechnicalSubsystemController controller = new TechnicalSubsystemController();

        float dt = 1f / 60f;

        System.out.println("=== Phase 1: Acceleration + Braking ===");

        for (int i = 0; i < 600; i++) {

            float time = i * dt;

            float throttle = (time < 5f) ? 1f : 0f;
            float brake    = (time >= 5f) ? 1f : 0f;
            float steering = 0f;

            controller.update(throttle, brake, steering, dt);

            if (i % 60 == 0) {
                System.out.printf("Time: " + time + " Dir: " + controller.getOrientationString() + " speed: " +  controller.getSpeedKmh() + " km/h " + " pos: " + controller.getPositionString() +"\n");
            }
        }

        System.out.println("\n=== Phase 2: Steering Test ===");

        for (int i = 0; i < 1800; i++) {  // 30 seconds at 60 FPS

            float time = 10f + i * dt;

            float throttle = 1.0f;   // FULL throttle to make turning real
            float brake = 0f;

            float steering;
            if (time < 20f) steering = 1f;        // turn left for 10 seconds
            else if (time < 25f) steering = -1f;  // then right for 5 seconds
            else steering = 0f;                   // straighten

            controller.update(throttle, brake, steering, dt);

            if (i % 60 == 0) {
                System.out.printf("Time: " + time + " Dir: " + controller.getOrientationString() + " speed: " +  controller.getSpeedKmh() + " km/h " + " pos: " + controller.getPositionString() +"\n" );
            }
        }

        System.out.println("Simulation complete.");
    }
}
