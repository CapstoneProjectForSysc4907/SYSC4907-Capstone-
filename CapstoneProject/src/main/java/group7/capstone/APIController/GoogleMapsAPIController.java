package group7.capstone.APIController;

import okhttp3.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class GoogleMapsAPIController {
    public static final MediaType JSON = MediaType.get("application/json");

    OkHttpClient client;

    public GoogleMapsAPIController() {
        this.client = new OkHttpClient();
    }

    public BufferedImage GetStreetViewImage(String lat, String lon, String head) throws IOException {

        String url = APIConfig.BASE_URL_STREETVIEW + "&heading=" + head + "&location=" + lat + ", " + lon + "&key=" + APIConfig.getAPIKey();

        Request request = new Request.Builder()
                .url(url)
                .build();
        try (Response response = client.newCall(request).execute()) {
            assert response.body() != null;
            return ImageIO.read(response.body().byteStream());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
