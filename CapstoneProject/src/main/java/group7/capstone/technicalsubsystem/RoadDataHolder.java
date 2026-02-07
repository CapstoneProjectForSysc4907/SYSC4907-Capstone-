package group7.capstone.technicalsubsystem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Singleton store for the current road polyline geo points (technicalsubsystem.RoadSegment).
 *
 * Key fixes vs your original:
 *  - getRoadList() does NOT leak the internal mutable ArrayList
 *  - removeRoadData() returns boolean (so you know if it actually removed)
 *  - helper methods for setting/replacing the whole road safely
 */
public final class RoadDataHolder {

    private static volatile RoadDataHolder instance;

    // Internal mutable storage (do NOT expose directly)
    private final ArrayList<RoadSegment> roadList;

    private RoadDataHolder() {
        this.roadList = new ArrayList<>();
    }

    public static RoadDataHolder getInstance() {
        // Double-checked locking (safe + fast)
        RoadDataHolder local = instance;
        if (local == null) {
            synchronized (RoadDataHolder.class) {
                local = instance;
                if (local == null) {
                    local = new RoadDataHolder();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Adds a single geo point. */
    public synchronized void addRoadData(RoadSegment road) {
        if (road == null) return;
        roadList.add(road);
    }

    /** Removes a point. Returns true if it was present and removed. */
    public synchronized boolean removeRoadData(RoadSegment road) {
        if (road == null) return false;
        return roadList.remove(road);
    }

    /**
     * Clears the current road and returns a copy of what was stored.
     * (Useful for debugging or undo.)
     */
    public synchronized ArrayList<RoadSegment> emptyRoad() {
        ArrayList<RoadSegment> oldRoads = new ArrayList<>(roadList);
        roadList.clear();
        return oldRoads;
    }

    /**
     * Replace the entire road with the provided points.
     * This avoids partially-updated lists when rebuilding.
     */
    public synchronized void setRoad(List<RoadSegment> points) {
        roadList.clear();
        if (points == null) return;
        for (RoadSegment p : points) {
            if (p != null) roadList.add(p);
        }
    }

    /** Returns a COPY so callers cannot mutate internal state. */
    public synchronized ArrayList<RoadSegment> getRoadList() {
        return new ArrayList<>(roadList);
    }

    /** Read-only view copy (often nicer for callers). */
    public synchronized List<RoadSegment> getRoadListReadOnly() {
        return Collections.unmodifiableList(new ArrayList<>(roadList));
    }

    public synchronized int size() {
        return roadList.size();
    }

    public synchronized boolean isEmpty() {
        return roadList.isEmpty();
    }
}