package group7.capstone.APIController;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class StreetViewImage {
    private BufferedImage image;
    private double lat, lon;
    private int head;

    public StreetViewImage(BufferedImage image, double lat, double lon, int head) {
        this.image = image;
        this.lat = lat;
        this.lon = lon;
        this.head = head;
    }

    public BufferedImage getImage() {
        return image;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public int getHead() {
        return head;
    }

    public void saveImageToFile(String formatName, String outputPath) {
        try {
            File outputFile = new File(outputPath+"."+formatName);
            if (ImageIO.write(image, formatName, outputFile)) {
                System.out.println("Image saved successfully to: " + outputFile.getAbsolutePath());
            } else {
                System.out.println("No appropriate writer found for format: " + formatName);
            }
        } catch (IOException e) {
            System.err.println("Error saving image: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
