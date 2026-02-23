package group7.capstone.technicalsubsystem;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.util.concurrent.atomic.AtomicBoolean;

public class InputHandler implements NativeKeyListener {

    // Key state (thread-safe enough for this use)
    private static final AtomicBoolean forward = new AtomicBoolean(false);
    private static final AtomicBoolean brake   = new AtomicBoolean(false);
    private static final AtomicBoolean left    = new AtomicBoolean(false);
    private static final AtomicBoolean right   = new AtomicBoolean(false);

    // Optional: a kill switch your main loop can watch
    private static final AtomicBoolean exitRequested = new AtomicBoolean(false);

    @Override
    public void nativeKeyPressed(NativeKeyEvent e) {
        switch (e.getKeyCode()) {
            case NativeKeyEvent.VC_W:
            case NativeKeyEvent.VC_UP:
                forward.set(true);
                break;

            case NativeKeyEvent.VC_S:
            case NativeKeyEvent.VC_DOWN:
                brake.set(true);
                break;

            case NativeKeyEvent.VC_A:
            case NativeKeyEvent.VC_LEFT:
                left.set(true);
                break;

            case NativeKeyEvent.VC_D:
            case NativeKeyEvent.VC_RIGHT:
                right.set(true);
                break;

            case NativeKeyEvent.VC_ESCAPE:
                exitRequested.set(true);
                try {
                    GlobalScreen.unregisterNativeHook();
                } catch (NativeHookException ex) {
                    // don’t crash the sim because unhook failed
                    ex.printStackTrace();
                }
                break;

            default:
                // ignore
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent e) {
        switch (e.getKeyCode()) {
            case NativeKeyEvent.VC_W:
            case NativeKeyEvent.VC_UP:
                forward.set(false);
                break;

            case NativeKeyEvent.VC_S:
            case NativeKeyEvent.VC_DOWN:
                brake.set(false);
                break;

            case NativeKeyEvent.VC_A:
            case NativeKeyEvent.VC_LEFT:
                left.set(false);
                break;

            case NativeKeyEvent.VC_D:
            case NativeKeyEvent.VC_RIGHT:
                right.set(false);
                break;

            default:
                // ignore
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent e) {
        // ignore (typed is not useful for continuous controls)
    }

    // ---- Read-only access for your sim loop ----
    public static boolean isForward() { return forward.get(); }
    public static boolean isBrake()   { return brake.get(); }
    public static boolean isLeft()    { return left.get(); }
    public static boolean isRight()   { return right.get(); }
    public static boolean isExitRequested() { return exitRequested.get(); }
    public static void requestExit() {
        exitRequested.set(true);
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (Exception ignored) {
        }
    }
}