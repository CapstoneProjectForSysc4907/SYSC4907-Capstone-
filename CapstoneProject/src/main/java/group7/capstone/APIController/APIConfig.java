package group7.capstone.APIController;

public class APIConfig {
    public static String BASE_URL_STREETVIEW = "https://maps.googleapis.com/maps/api/streetview?size=800x600&fov=120";
    public static String BASE_URL_SNAPTOROAD = "https://roads.googleapis.com/v1/snapToRoads";
    public static String Base_URL_MAP = "http://maps.googleapis.com/maps/api/staticmap?zoom=16&size=400x400";

    private static String APILogFile = "APILog.log";


    /**
     * returns the api key from the system environment
     * to add the api key to your system go to system properties, environment variables, then create MapsAPIKey
     */
    public static String getAPIKey(){
        return System.getenv("MapsAPIKey");
    }

    public static String getAPILogFile(){
        return APILogFile;
    }
}
