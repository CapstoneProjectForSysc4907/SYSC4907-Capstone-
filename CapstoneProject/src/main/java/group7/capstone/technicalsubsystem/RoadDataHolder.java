package group7.capstone.technicalsubsystem;
import java.util.ArrayList;

public class RoadDataHolder {
    private static RoadDataHolder instance;
    private ArrayList<RoadSegment> roadList;

    private RoadDataHolder() {
        this.roadList = new ArrayList<>();
    }

    public static RoadDataHolder getInstance() {
        if (instance == null) {
            instance = new RoadDataHolder();
        }
        return instance;
    }

    public void addRoadData(RoadSegment road) {
        roadList.add(road);
    }

    public RoadSegment removeRoadData(RoadSegment road) {
        roadList.remove(road);
        return road;
    }

    public ArrayList<RoadSegment> emptyRoad(){
        ArrayList<RoadSegment> oldRoads = new ArrayList<>();
        oldRoads.addAll(roadList);
        roadList.clear();
        return oldRoads;
    }

    public ArrayList<RoadSegment> getRoadList() {
        return roadList;
    }
}
