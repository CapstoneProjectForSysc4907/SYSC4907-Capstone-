package group7.capstone.APIController;

import java.awt.image.BufferedImage;

public class StreetViewImage {
    private BufferedImage image;
    private Float lat, lon;
    private int head;

    public StreetViewImage(BufferedImage image, Float lat, Float lon, int head) {
        this.image = image;
        this.lat = lat;
        this.lon = lon;
        this.head = head;
    }

    public BufferedImage getImage() {
        return image;
    }

    public Float getLat() {
        return lat;
    }

    public Float getLon() {
        return lon;
    }

    public int getHead() {
        return head;
    }
}
