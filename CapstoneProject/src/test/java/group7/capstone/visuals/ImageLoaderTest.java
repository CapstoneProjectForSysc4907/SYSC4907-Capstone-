package group7.capstone.visuals;

import group7.capstone.APIController.GoogleMapsAPIController;
import group7.capstone.APIController.StreetViewImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class ImageLoaderTest {

    private ImageLoader imageLoader;

    @AfterEach
    void tearDown() {
        if (imageLoader != null) {
            imageLoader.shutdown();
        }
    }

    @Test
    void loadStreetViewImageUsesCacheOnSecondCall() {
        FakeImageApi api = new FakeImageApi();
        imageLoader = new ImageLoader(api);

        BufferedImage first = imageLoader.loadStreetViewImage(45.4215, -75.6972, 90);
        BufferedImage second = imageLoader.loadStreetViewImage(45.4215, -75.6972, 90);

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(1, api.streetViewCalls);
        assertEquals(1, imageLoader.getCacheSize());
        assertEquals(1, imageLoader.getCacheHits());
    }

    @Test
    void nullApiImageReturnsPlaceholder() {
        FakeImageApi api = new FakeImageApi();
        api.returnNullStreetView = true;
        imageLoader = new ImageLoader(api);

        BufferedImage result = imageLoader.loadStreetViewImage(45.4215, -75.6972, 90);

        assertNotNull(result);
        assertEquals(800, result.getWidth());
        assertEquals(600, result.getHeight());
        assertEquals(1, imageLoader.getFailedLoads());
    }

    @Test
    void clearCacheRemovesStoredImages() {
        FakeImageApi api = new FakeImageApi();
        imageLoader = new ImageLoader(api);

        imageLoader.loadStreetViewImage(45.4215, -75.6972, 90);
        assertEquals(1, imageLoader.getCacheSize());

        imageLoader.clearCache();
        assertEquals(0, imageLoader.getCacheSize());
    }

    private static class FakeImageApi extends GoogleMapsAPIController {
        int streetViewCalls = 0;
        boolean returnNullStreetView = false;

        @Override
        public StreetViewImage GetStreetViewImage(double lat, double lon, int head) throws IOException {
            streetViewCalls++;

            if (returnNullStreetView) {
                return null;
            }

            BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, 0x00FF00);
            image.setRGB(1, 1, 0x0000FF);
            return new StreetViewImage(image, lat, lon, head);
        }

        @Override
        public BufferedImage GetMapImage(double lat, double lon) throws IOException {
            return new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        }
    }
}
