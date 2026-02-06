package group7.capstone.technicalsubsystem;

import java.util.ArrayList;
import java.util.List;

public class RoadDataHolder {
    private static RoadDataHolder instance;
    private ArrayList<RoadSegment> roadList;

    // increment whenever the polyline changes
    private int version = 0;

    private RoadDataHolder() {
        this.roadList = new ArrayList<>();
    }

    public static RoadDataHolder getInstance() {
        if (instance == null) instance = new RoadDataHolder();
        return instance;
    }

    public int getVersion() {
        return version;
    }

    // Existing single-point add (still useful for testing / manual input)
    public void addRoadData(RoadSegment road) {
        roadList.add(road);
        version++;
    }

    public RoadSegment removeRoadData(RoadSegment road) {
        roadList.remove(road);
        version++;
        return road;
    }

    public ArrayList<RoadSegment> emptyRoad() {
        ArrayList<RoadSegment> oldRoads = new ArrayList<>(roadList);
        roadList.clear();
        version++;
        return oldRoads;
    }

    public ArrayList<RoadSegment> getRoadList() {
        return roadList;
    }

    // ===================== NEW (STEP 1) =====================

    /**
     * Replace the entire road polyline at once.
     * Points are interpreted as an ordered polyline:
     * p0 -> p1 -> p2 -> ... -> pn
     */
    public void setRoadPolyline(List<RoadSegment> points) {
        roadList.clear();

        if (points != null) {
            for (RoadSegment p : points) {
                if (p != null) {
                    roadList.add(p);
                }
            }
        }

        version++; // single, atomic change
    }
}
