package group7.capstone.APIController;

public class APIConfig {
    public static String BASE_URL_STREETVIEW = "https://maps.googleapis.com/maps/api/streetview?size=400x400&fov=120";
    public static String BASE_URL_SNAPTOROAD = "https://roads.googleapis.com/v1/snapToRoads";

    private static String APILogFile = "APILog.log";

    public static String getAPIKey(){
        /*returns the api key from the system environment
        to add the api key to your system go to system properties, environment variables, then create MapsAPIKey
         */
        return System.getenv("MapsAPIKey");
    }

    public static String getAPILogFile(){
        return APILogFile;
    }
}
