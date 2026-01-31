package group7.capstone.APIController;

import okhttp3.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
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

    
}
