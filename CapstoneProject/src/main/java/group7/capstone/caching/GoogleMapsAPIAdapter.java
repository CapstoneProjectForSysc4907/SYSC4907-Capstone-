package group7.capstone.caching;

import group7.capstone.APIController.APIResponseDomain;
import java.util.ArrayList;
import java.util.List;

/**
 * Adapter that bridges the real GoogleMapsAPIController to the caching interface.
 *
 * This allows CacheManager to work with the actual API implementation without
 * modifying either the CacheManager or the existing GoogleMapsAPIController.
 */
public class GoogleMapsAPIAdapter implements GoogleMapsAPIController {

    private final group7.capstone.APIController.GoogleMapsAPIController realApiController;

    /**
     * Create adapter wrapping the real API controller
     * @param realApiController The actual Google Maps API controller
     */
    public GoogleMapsAPIAdapter(group7.capstone.APIController.GoogleMapsAPIController realApiController) {
        if (realApiController == null) {
            throw new IllegalArgumentException("Real API controller cannot be null");
        }
        this.realApiController = realApiController;
    }

    @Override
    public List<RoadSegment> getRoadData(double lat, double lng, double radiusKm) {
        // The real API controller uses getStreet which takes heading, not radius
        // We'll make multiple calls in different directions to simulate radius coverage

        List<RoadSegment> allSegments = new ArrayList<>();

        // Sample 4 directions (N, E, S, W) to get road coverage around the point
        int[] headings = {0, 90, 180, 270};

        for (int heading : headings) {
            try {
                APIResponseDomain response = realApiController.getStreet(lat, lng, heading);

                if (response != null && response.getSnappedPoints() != null) {
                    // Convert APIResponseDomain to RoadSegment (caching version)
                    RoadSegment segment = new RoadSegment(response);

                    // Only add if it has points
                    if (!segment.getPoints().isEmpty()) {
                        allSegments.add(segment);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to get road data for heading " + heading + ": " + e.getMessage());
            }
        }

        return allSegments;
    }

    @Override
    public StreetViewImage getStreetViewImage(double lat, double lng, int heading) {
        try {
            // Call real API controller
            group7.capstone.APIController.StreetViewImage realImage =
                    realApiController.GetStreetViewImage(lat, lng, heading);

            if (realImage == null) {
                return null;
            }

            // Convert real StreetViewImage to caching version
            // The caching version expects byte[] but real version has BufferedImage
            // We need to convert BufferedImage to byte[]

            java.awt.image.BufferedImage bufferedImage = realImage.getImage();
            if (bufferedImage == null) {
                return null;
            }

            // Convert BufferedImage to byte array
            byte[] imageBytes = convertBufferedImageToBytes(bufferedImage);

            return new StreetViewImage(
                    imageBytes,
                    heading,
                    lat,
                    lng
            );

        } catch (Exception e) {
            System.err.println("Failed to get street view image: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert BufferedImage to byte array (PNG format)
     */
    private byte[] convertBufferedImageToBytes(java.awt.image.BufferedImage image) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            System.err.println("Failed to convert image to bytes: " + e.getMessage());
            return new byte[0];
        }
    }

    public group7.capstone.APIController.GoogleMapsAPIController getRealController() {
        return realApiController;
    }
}