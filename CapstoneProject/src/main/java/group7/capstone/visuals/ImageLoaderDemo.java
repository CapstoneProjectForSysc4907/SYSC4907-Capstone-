package group7.capstone.visuals;

import group7.capstone.APIController.GoogleMapsAPIController;

import java.awt.image.BufferedImage;
import javax.swing.*;

/**
 * demo for ImageLoader.
 */
public class ImageLoaderDemo {

    public static void main(String[] args) {
        System.out.println("ImageLoader Demo\n");

        GoogleMapsAPIController apiController = new GoogleMapsAPIController();
        ImageLoader imageLoader = new ImageLoader(apiController);

        // Load and display three locations
        loadAndShow(imageLoader, 45.423599, -75.701050, 0,   "Ottawa: Parliament Hill");
        loadAndShow(imageLoader, 43.642567, -79.387054, 90,  "Toronto: CN Tower");
        loadAndShow(imageLoader, 45.505556, -73.551389, 180, "Montreal: Old Port");

        // Confirm cache hit on the first image
        System.out.println("\nReloading Ottawa (should be a cache hit)...");
        long start = System.currentTimeMillis();
        imageLoader.loadStreetViewImage(45.4215, -75.6972, 0);
        System.out.println("Cached load took " + (System.currentTimeMillis() - start) + " ms");
        System.out.println(imageLoader.getImageLoadStats());

        imageLoader.shutdown();
    }

    private static void loadAndShow(ImageLoader loader, double lat, double lng, int heading, String label) {
        System.out.print("Loading " + label);
        BufferedImage image = loader.loadStreetViewImage(lat, lng, heading);

        if (image != null) {
            System.out.println("VALID (" + image.getWidth() + "x" + image.getHeight() + ")");
            showInWindow(image, label);
        } else {
            System.out.println("FAILED");
        }
    }
    /**
     * This is only for the demo, GUI will take care of this
     */
    private static void showInWindow(BufferedImage image, String title) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Street View – " + title);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setSize(image.getWidth() + 40, image.getHeight() + 60);
            frame.setLocationRelativeTo(null);
            frame.add(new JLabel(new ImageIcon(image)));
            frame.setVisible(true);
        });
    }
}