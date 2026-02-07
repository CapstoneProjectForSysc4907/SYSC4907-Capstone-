package group7.capstone.APIController;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import group7.capstone.caching.RoadSegment;
import group7.capstone.caching.StreetViewImage;
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

    public GoogleMapsAPIController() {
        this.client = new OkHttpClient();
        try {
            FileHandler fh = new FileHandler(APIConfig.getAPILogFile()); // Log to a file named "mylog.log"
            logger.addHandler(fh);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public StreetViewImage GetStreetViewImage(double lat, double lon, int head) throws IOException {
        /*
        gets an image from the google maps static streetview api
        latitude and longitude are in degrees from prime meridian and equator
        heading is in degrees from north
        returns a 120 slice of the panorama as a buffered image
        each costs 7 dollars per 1000 calls with 10,000 call buffer
         */
        logger.info("requesting image from: lat=" + lat + ", lon=" + lon + ", heading=" + head);
        String url = APIConfig.BASE_URL_STREETVIEW + "&heading=" + head + "&location=" + lat + ", " + lon + "&key=" + APIConfig.getAPIKey();

        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            logger.info("result returned is probably image");
            return new StreetViewImage(response.body().bytes(), head, lat,
                    lon);
        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
    }

    public double[] calculateNewCoords(double lat, double lon, int head){
        double dist = 0.00025;
        double rHead = head*Math.PI/180;
        double newlat = lat + Math.cos(rHead) * dist;
        double newlon = lon + Math.sin(rHead) * dist;
        return new double[]{newlat, newlon};
    }

    public String getPath(double lat, double lon, int head, int steps){
        String newPath = lat + "," + lon;
        double[] next = calculateNewCoords(lat,lon,head);
        if (steps > 0){
            newPath += "|" + getPath(next[0],next[1],head,steps-1);
        }
        return newPath;
    }

    public APIResponseDomain getStreet(double lat, double lon, int head) {
        logger.info("finding closest road to: lat=" + lat + ", lon=" + lon);
        //String url = APIConfig.BASE_URL_SNAPTOROAD + "?interpolate=true&path=45.424061778387276,-75.40926382929229|45.42590246784146,-75.4102358132577&key=" + APIConfig.getAPIKey();
        String url = APIConfig.BASE_URL_SNAPTOROAD + "?interpolate=true&path=" + getPath(lat, lon, head, 95) + "&key=" + APIConfig.getAPIKey();
        System.out.println(url);
        Request request = new Request.Builder()
                .url(url)
                .build();

        ArrayList<APIResponseDomain.SnappedPoint> segments = new ArrayList<>();
        APIResponseDomain responseDomain = new APIResponseDomain();

        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            logger.info("result returned properly");
            Map<String, Object> jsonResponseObject = new Gson().fromJson(response.body().charStream(), Map.class);
            ArrayList<Map> points = (ArrayList<Map>) jsonResponseObject.get("snappedPoints");
            Map<String, Object> location;

            for (Map<String,Object> p: points) {
                segments.add(new APIResponseDomain.SnappedPoint());
                APIResponseDomain.SnappedPoint s = segments.get(segments.size() - 1);
                s.setLocation(new APIResponseDomain.LatLng());
                location = (Map<String, Object>) p.get("location");
                s.getLocation().setLatitude((Double) location.get("latitude"));
                s.getLocation().setLongitude((Double) location.get("longitude"));
                s.setPlaceId((String) p.get("placeId"));
                if(p.containsKey("originalIndex")){
                    System.out.println("has origin");
                    double index = (double) p.get("originalIndex");
                    s.setOriginalIndex((int) index);
                }
            }
            responseDomain.setSnappedPoints(segments);
        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
        logger.info("exiting saveStreet function");
        System.out.println(responseDomain.getSnappedPoints().size());
        return responseDomain;
    }


    public void saveStreetViews(APIResponseDomain road, String head){
        //saves the images in a file for testing purposes
        /* TODO: fix after api controller changes.
        int num = 0;

        try{
            for(APIResponseDomain.SnappedPoint p : road.getSnappedPoints()){
                System.out.println(p.toString());
                StreetViewImage image = GetStreetViewImage(p.getLocation().getLatitude(),p.getLocation().getLongitude(),head);
                String imgName = p.getPlaceId() + num;
                data.add(new String[]{newLat, newLon, head, imgName});
                image.saveImageToFile("png", "preload/" + imgName);
                num += 1;
            }
            try (CSVWriter writer = new CSVWriter(new FileWriter(filePath))) {
                writer.writeAll(data); // Write all data at once
            } catch (IOException e) {
                e.printStackTrace();
            }
            //System.out.println(response.body().string());
        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
        */
    }
}
