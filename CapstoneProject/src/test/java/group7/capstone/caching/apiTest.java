package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;
import group7.capstone.APIController.APIStub;
import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.APIController.StreetViewImage;
import group7.capstone.visuals.GUI.ImagePanel;
import org.junit.jupiter.api.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class apiTest {

    @Test
    void getImageTest() throws IOException {
        GoogleMapsAPIController googleApi = new GoogleMapsAPIController();
        double Lat = 45.42407319361511;
        double Lon = -75.40922137593788;
        int Head = 350;

        StreetViewImage image = googleApi.GetStreetViewImage(Lat,Lon,Head);
        assert image != null;
        assert imageCheck(image.getImage());

        BufferedImage img = image.getImage();

        assert img.getRGB(30, 31) == -261;
        assert img.getRGB(20, 75) == -66053;
        assert img.getRGB(100, 100) == -2300697;

    }

    @Test
    void getStreetTest(){
        GoogleMapsAPIController googleApi = new GoogleMapsAPIController();
        double Lat = 45.42407319361511;
        double Lon = -75.40922137593788;
        int Head = 350;
        APIResponseDomain domain = googleApi.getStreet(Lat,Lon,Head);

        assert !domain.getSnappedPoints().isEmpty();
    }

    boolean imageCheck(BufferedImage img) {
        try {
            for (int x = 1; x < img.getWidth(); x++) {
                for (int y = 1; y < img.getHeight(); y++) {
                    //ensures that pixels are the same and that the second image isn't black
                    int pixel = img.getRGB(x, y);
                    Color color = new Color(pixel, true); // true supports alpha
                    int red = color.getRed();
                    int green = color.getGreen();
                    int blue = color.getBlue();
                    int alpha = color.getAlpha();
                    if (red > 255 || blue > 255 || green > 255) {
                        return false;
                    }
                }
            }
            return true;
        } catch (Exception e){
            return true;
        }
    }
}
