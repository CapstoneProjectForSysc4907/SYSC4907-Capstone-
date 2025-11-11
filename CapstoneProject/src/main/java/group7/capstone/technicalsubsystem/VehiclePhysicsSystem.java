package group7.capstone.technicalsubsystem;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.BoxCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.math.Vector3f;

public class VehiclePhysicsSystem {

    private final PhysicsRigidBody vehicleBody;

    public VehiclePhysicsSystem(PhysicsRigidBody body) {
        this.vehicleBody = body;
    }

    public void changeSpeed(float throttle, float brake) {
        float netForce = (500f * throttle) - (300f * brake);
        Vector3f forward = vehicleBody.getPhysicsRotation().mult(Vector3f.UNIT_Z);
        vehicleBody.applyCentralForce(forward.mult(netForce));
    }

    public Vector3f getPosition() {
        return vehicleBody.getPhysicsLocation();
    }

    public float getSpeedKmh() {
        return vehicleBody.getLinearVelocity().length() * 3.6f;
    }
}
