package group7.capstone;

import com.jme3.system.NativeLibraryLoader;
import group7.capstone.APIController.APIConfig;
import group7.capstone.APIController.APIStub;
import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.APIController.StreetViewImage;
import group7.capstone.technicalsubsystem.MapObject;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;
import group7.capstone.technicalsubsystem.VehiclePhysicsSystem;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        /*
        demonstration for saving a test image
*/
        GoogleMapsAPIController controller1 = new GoogleMapsAPIController();
        //45.424061778387276,-75.40926382929229|45.42590246784146,-75.4102358132577

        //controller1.GetClosestStreetView("45.53923474756772", "-76.71215350154397", "0");,
        //controller1.saveStreetViews("45.424061778387276","-75.40926382929229","45.42568923189714","-75.41011401787613", "350");
        StreetViewImage img = APIStub.getClosestImage("45.53923474756772", "-76.71215350154397");
        //img.saveImageToFile("png", "test2.png ");

        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);

        TechnicalSubsystemController controller = new TechnicalSubsystemController();

      /*  float dt = 1f / 60f;

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

        System.out.println("Simulation complete.");*/
        testDrive(controller);
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
            }
            else if (simTime < 14f) {
                throttle = 1.0f;
                steering = 1.0f;
            }
            else {
                brake = 1.0f;
            }

            controller.update(throttle, brake, steering, dt);
            simTime += dt;

            if (((int)(simTime * 10)) % 5 == 0) {
                System.out.println(
                        "t=" + String.format("%.2f", simTime) +
                                "s | Pos=" + controller.getPosition() +
                                " | Speed=" + String.format("%.1f", controller.getSpeedKmh()) + " km/h" +
                                " | Dir=" + controller.getOrientationString()
                );
            }
        }

        System.out.println("---- Road Test Complete ----");
    }




}
