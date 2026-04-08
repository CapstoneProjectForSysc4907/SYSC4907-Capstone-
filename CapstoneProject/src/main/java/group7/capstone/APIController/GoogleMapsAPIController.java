package group7.capstone.APIController;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import group7.capstone.caching.RoadSegment;
import group7.capstone.technicalsubsystem.TechnicalSubsystemController;
import okhttp3.*;

import javax.imageio.ImageIO;
import com.opencsv.CSVWriter;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.FileHandler;
import java.util.logging.Logger;

public class GoogleMapsAPIController {
    public static final MediaType JSON = MediaType.get("application/json");
    private static final Logger logger = Logger.getLogger(GoogleMapsAPIController.class.getName());

    OkHttpClient client;

    Optional <TechnicalSubsystemController> techController;


    public GoogleMapsAPIController() {
        this.client = new OkHttpClient();
        this.techController = Optional.empty();
        try {
            FileHandler fh = new FileHandler(APIConfig.getAPILogFile()); // Log to a file named "mylog.log"
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public GoogleMapsAPIController(TechnicalSubsystemController tech) {
        this.client = new OkHttpClient();
        try {
            FileHandler fh = new FileHandler(APIConfig.getAPILogFile()); // Log to a file named "mylog.log"
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.techController = Optional.ofNullable(tech);
    }

    public void setTechController(Optional<TechnicalSubsystemController> techController) {
        this.techController = techController;
    }

    public BufferedImage GetMapImage(double lat, double lon) throws IOException {
        /*
        gets an image from the google maps static streetview api
        latitude and longitude are in degrees from prime meridian and equator
        heading is in degrees from north
        returns a 120 slice of the panorama as a buffered image
        each costs 7 dollars per 1000 calls with 10,000 call buffer
         */
        logger.info("requesting map from: lat=" + lat + ", lon=" + lon);
        String url = APIConfig.Base_URL_MAP + "&markers=" + lat + ", " + lon + "&key=" + APIConfig.getAPIKey();
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            logger.info("result returned is probably map");

            return ImageIO.read(response.body().byteStream());

        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
    }

    /**
     * returns the static street view from the Google Maps API
     *
     * @param lat the latitude of the simulated car
     * @param lon the longitude of the simulated car
     * @param head the heading in degrees from north
     * @return StreetViewImage object containing an image and associated coordinates
     * @throws IOException
     */
    public StreetViewImage GetStreetViewImage(double lat, double lon, int head) throws IOException {
        logger.info("requesting image from: lat=" + lat + ", lon=" + lon + ", heading=" + head);
        String url = APIConfig.BASE_URL_STREETVIEW + "&heading=" + head + "&location=" + lat + ", " + lon + "&key=" + APIConfig.getAPIKey();

        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            logger.info("result returned is probably image");

            return new StreetViewImage(ImageIO.read(response.body().byteStream()), lat, lon, head);

        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
    }

    /**
     * calculates the coordinates of the point a set distance ahead of the car
     *
     * @param lat the latitude of the car
     * @param lon the longitude of the car
     * @param head the heading of the car
     * @return a pair of coordinates representing the cars new position.
     */
    public double[] calculateNewCoords(double lat, double lon, int head){
        double dist = 0.00004;
        double rHead = head*Math.PI/180;
        double newlat = lat + Math.cos(rHead) * dist;
        double newlon = lon + Math.sin(rHead) * dist;
        return new double[]{newlat, newlon};
    }

    /**
     * gathers steps number of evenly spaced coordinates ahead of the car
     *
     * @param lat the latitude of the car
     * @param lon the longitude of the car
     * @param head the heading of the car
     * @param steps number of points the car will map out
     * @return a string containing the coordinates that will be snapped to the nearest road
     */
    public String getPath(double lat, double lon, int head, int steps){
        String newPath = lat + "," + lon;
        double[] next = calculateNewCoords(lat,lon,head);
        if (steps > 0){
            newPath += "|" + getPath(next[0],next[1],head,steps-1);
        }
        return newPath;
    }

    /**
     * maps out the road ahead of the car based on its coordinates and heading
     *
     * @param lat the latitude coordinate of the car
     * @param lon the longitude coordinate of the car
     * @param head the heading of the car (0 and 360  are north)
     * @return an APIResponseDomain object containing a list of coordinates that form the road
     */
    public APIResponseDomain getStreet(double lat, double lon, int head) {
        logger.info("finding closest road to: lat=" + lat + ", lon=" + lon);

        //Block 1
        String url = APIConfig.BASE_URL_SNAPTOROAD + "?interpolate=true&path=" + getPath(lat, lon, head, 10) + "&key=" + APIConfig.getAPIKey();
        Request request = new Request.Builder()
                .url(url)
                .build();

        ArrayList<APIResponseDomain.SnappedPoint> segments = new ArrayList<>();
        APIResponseDomain responseDomain = new APIResponseDomain();

        //Block 2
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            logger.info("result returned properly");
            Map jsonResponseObject = new Gson().fromJson(response.body().charStream(), Map.class);
            ArrayList<Map> points = (ArrayList<Map>) jsonResponseObject.get("snappedPoints");
            Map<String, Object> location;

            //Block 3
            for (Map<String,Object> p: points) {
                segments.add(new APIResponseDomain.SnappedPoint());
                APIResponseDomain.SnappedPoint s = segments.get(segments.size() - 1);
                s.setLocation(new APIResponseDomain.LatLng());
                location = (Map<String, Object>) p.get("location");
                s.getLocation().setLatitude((Double) location.get("latitude"));
                s.getLocation().setLongitude((Double) location.get("longitude"));
                s.setPlaceId((String) p.get("placeId"));
                if(p.containsKey("originalIndex")){
                    double index = (double) p.get("originalIndex");
                    s.setOriginalIndex((int) index);
                }
            }
            responseDomain.setSnappedPoints(segments);

        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
        logger.info("exiting getStreet function");
        return responseDomain;
    }
}