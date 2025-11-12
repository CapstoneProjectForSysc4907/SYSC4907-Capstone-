package group7.capstone;
import com.jme3.system.NativeLibraryLoader;
import group7.capstone.technicalsubsystem.CarObject;
import group7.capstone.technicalsubsystem.MapObject;
import com.jme3.bullet.util.NativeLibrary;



public class Main {
    public static void main(String[] args) {
        NativeLibraryLoader.loadNativeLibrary("bulletjme", true);
        MapObject map = new MapObject();

        CarObject car = new CarObject("Car_01", map);

        float dt = 1f / 60f;
        for (int i = 0; i < 600; i++) {
            float time = i * dt;

            float throttle = (time < 5f) ? 1f : 0f;
            float brake    = (time >= 5f) ? 1f : 0f;

            car.accelerate(throttle);
            car.brake(brake);

            map.step(dt);

            if (i % 60 == 0) {
                System.out.printf("t=%.1fs  pos=%s  speed=%.1f km/h%n",
                        time, car.getPosition(), car.getSpeed());
            }
        }

        System.out.println("Done");
        }
    }
