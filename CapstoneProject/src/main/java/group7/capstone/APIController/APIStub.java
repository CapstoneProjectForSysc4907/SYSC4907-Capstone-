package group7.capstone.APIController;

import com.google.gson.Gson;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class APIStub {

    public static StreetViewImage getClosestImage(String lat, String lon){
        String filePath = "preload/imagefinder.csv";
        Map<Double,Object> data = new HashMap<>();
        double closest = 10000000.00000;

        // Using try-with-resources to ensure the reader is closed
        try (CSVReader reader = new CSVReader(new FileReader(filePath))) {
            String[] nextLine;
            while ((nextLine = reader.readNext()) != null) {
                double distance = dist(Double.parseDouble(lat),Double.parseDouble(lon),Double.parseDouble(nextLine[0]),Double.parseDouble(nextLine[1]));
                String path = "preload/" + nextLine[3] + ".png";
                BufferedImage loadedImage = ImageIO.read(new File(path));
                StreetViewImage image = new StreetViewImage(loadedImage,Double.parseDouble(nextLine[0]),Double.parseDouble(nextLine[1]),Integer.parseInt(nextLine[2]));
                data.put(distance,image);
                if (distance<closest){
                    closest = distance;
                }
            }
        } catch (IOException | CsvValidationException e) {
            e.printStackTrace();
        }
        return (StreetViewImage) data.get(closest);
    }

    public static double dist(double x1, double y1, double x2, double y2){
        return Math.hypot(x2 - x1, y2 - y1);
    }
}
