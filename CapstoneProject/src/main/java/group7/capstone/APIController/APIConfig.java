package group7.capstone.APIController;

public class APIConfig {
    public static String BASE_URL_STREETVIEW = "https://maps.googleapis.com/maps/api/streetview?size=400x400&fov=120";

    public static String getAPIKey(){
        return System.getenv("MapsAPIKey");
    }
}
