package group7.capstone.visuals.GUI;

import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.visuals.ImageLoader;

import javax.swing.*;
import java.awt.image.BufferedImage;

public class GuiDemo {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SimulatorFrame frame = new SimulatorFrame();
            frame.setVisible(true);

            double lat = 45.4215;
            double lng = -75.6972;
            int heading = 100;

            frame.getHud().setLatLng(lat, lng);
            frame.getHud().setHeading(heading);
            frame.getHud().setSpeed(0);
            frame.getHud().setStatus("loading");
            frame.setFooterStatus("Requesting Street View image");
            frame.setStreetViewImage(new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB));

            GoogleMapsAPIController api = new GoogleMapsAPIController();
            ImageLoader loader = new ImageLoader(api);

            // load in background
            new Thread(() -> {
                try {
                    BufferedImage img = loader.loadStreetViewImage(lat, lng, heading);

                    SwingUtilities.invokeLater(() -> {
                        frame.setStreetViewImage(img);
                        frame.getHud().setStatus("ok");
                        frame.setFooterStatus("Street View loaded.");
                    });

                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        frame.getHud().setStatus("error");
                        frame.setFooterStatus("Error loading Street View: " + e.getMessage());
                    });
                }
            }).start();
        });
    }
}
