package group7.capstone.APIController;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
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

    public StreetViewImage GetStreetViewImage(String lat, String lon, String head) throws IOException {
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
            return new StreetViewImage(ImageIO.read(response.body().byteStream()), Float.parseFloat(lat),
                    Float.parseFloat(lon), Integer.parseInt(head));
        } catch (IOException e) {
            logger.warning("google api call failed");
            throw new RuntimeException(e);
        }
    }

    public double[] calculateNewCoords(double lat, double lon, String head){
        double dist = 0.0005;
        double rHead = (Double.parseDouble(head))*Math.PI/180;
        double newlat = lat + Math.cos(rHead) * dist;
        double newlon = lon + Math.sin(rHead) * dist;
        return new double[]{newlat, newlon};
    }

    public String getPath(double lat, double lon, String head, int steps){
        String newPath = lat + "," + lon;
        double[] next = calculateNewCoords(lat,lon,head);
        if (steps > 0){
            newPath += "|" + getPath(next[0],next[1],head,steps-1);
        }
        return newPath;
    }

    public void saveStreetViews(String lat, String lon, String head){
        logger.info("finding closest road to: lat=" + lat + ", lon=" + lon);
        //String url = APIConfig.BASE_URL_SNAPTOROAD + "?interpolate=true&path=45.424061778387276,-75.40926382929229|45.42590246784146,-75.4102358132577&key=" + APIConfig.getAPIKey();
        String url = APIConfig.BASE_URL_SNAPTOROAD + "?interpolate=true&path=" + getPath(Double.parseDouble(lat),Double.parseDouble(lon),head,40) + "&key=" + APIConfig.getAPIKey();
        System.out.println(url);
        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            logger.info("result returned properly");
            Map<String, Object> jsonResponseObject = new Gson().fromJson(response.body().charStream(), Map.class);
            ArrayList<Map> points = (ArrayList<Map>) jsonResponseObject.get("snappedPoints");
            String newLat = "";
            String newLon = "";
            int num = 0;
            List<String[]> data = new ArrayList<>();

            try {
                FileWriter myWriter = new FileWriter("preload/response.txt");
                myWriter.write(jsonResponseObject.toString());
                myWriter.close();  // must close manually
                System.out.println("Successfully wrote to the file.");
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }

            String filePath = "preload/imagefinder.csv";

            // Using try-with-resources to ensure the reader is closed
            try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
                String[] nextLine;
                while ((nextLine = reader.readNext()) != null) {
                    // nextLine is an array of strings for the current line
                    System.out.println(Arrays.toString(nextLine));
                    data.add(nextLine);
                    num += 1;
                }
            } catch (IOException | CsvValidationException e) {
                e.printStackTrace();
            }

            for(Map p : points){
                Map location = (Map) p.get("location");
                System.out.println(p.toString());
                newLat = location.get("latitude").toString();
                newLon = location.get("longitude").toString();
                StreetViewImage image = GetStreetViewImage(newLat,newLon,head);
                String imgName = p.get("placeId").toString() + num;
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
    }
}
