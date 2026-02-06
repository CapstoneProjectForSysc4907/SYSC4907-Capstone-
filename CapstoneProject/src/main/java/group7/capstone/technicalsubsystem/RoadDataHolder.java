package group7.capstone.technicalsubsystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoadDataHolder {

    private static RoadDataHolder instance;

    private final Object lock = new Object();
    private final ArrayList<RoadSegment> roadList;

    // Increment whenever the polyline changes
    private int version = 0;

    private RoadDataHolder() {
        this.roadList = new ArrayList<>();
    }

    public static RoadDataHolder getInstance() {
        if (instance == null) instance = new RoadDataHolder();
        return instance;
    }

    /** Version for change detection */
    public int getVersion() {
        synchronized (lock) {
            return version;
        }
    }

    // =====================
    // MUTATORS (WRITE)
    // =====================

    public void addRoadData(RoadSegment road) {
        if (road == null) return;
        synchronized (lock) {
            roadList.add(road);
            version++;
        }
    }

    public RoadSegment removeRoadData(RoadSegment road) {
        synchronized (lock) {
            boolean removed = roadList.remove(road);
            if (removed) version++;
            return road;
        }
    }

    public ArrayList<RoadSegment> emptyRoad() {
        synchronized (lock) {
            ArrayList<RoadSegment> old = new ArrayList<>(roadList);
            roadList.clear();
            version++;
            return old;
        }
    }

    /**
     * Replace the entire road polyline atomically.
     * p0 -> p1 -> p2 -> ... -> pn
     */
    public void setRoadPolyline(List<RoadSegment> points) {
        synchronized (lock) {
            roadList.clear();

            if (points != null) {
                for (RoadSegment p : points) {
                    if (p != null) roadList.add(p);
                }
            }

            version++; // single atomic change
        }
    }

    // =====================
    // READ (SNAPSHOT)
    // =====================

    /**
     * Returns a SNAPSHOT of the road list.
     * Callers may iterate safely.
     */
    public List<RoadSegment> getRoadListSnapshot() {
        synchronized (lock) {
            return new ArrayList<>(roadList);
        }
    }

    /**
     * Legacy method — avoid using in new code.
     * Returns an unmodifiable view to prevent mutation.
     */
    @Deprecated
    public List<RoadSegment> getRoadList() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(roadList));
        }
    }
}
