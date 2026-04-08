package group7.capstone.visuals;

import group7.capstone.visuals.GUI.HudPanel;
import group7.capstone.visuals.GUI.ImagePanel;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class GuiPanelsTest {

    @Test
    void hudPanelSettersUpdateDisplayedValues() throws Exception {
        HudPanel hudPanel = new HudPanel();
        hudPanel.setSpeed(42.5);
        hudPanel.setLatLng(45.4215, -75.6972);
        hudPanel.setHeading(90);
        hudPanel.setStatus("loading");


        assertEquals("42.5 km/h", getLabelText(hudPanel, "speedValue"));
        assertEquals("45.4215, -75.6972", getLabelText(hudPanel, "latLngValue"));
        assertEquals("90", getLabelText(hudPanel, "headingValue"));
        assertEquals("loading", getLabelText(hudPanel, "statusValue"));
    }

    @Test
    void bufferedImagesEqualTest() throws Exception {
        ImagePanel panel = new ImagePanel();

        BufferedImage first = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        BufferedImage same = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        BufferedImage different = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);

        first.setRGB(5, 5, 0x00FF00);
        same.setRGB(5, 5, 0x00FF00);
        different.setRGB(5, 5, 0x0000FF);

        assertTrue(panel.bufferedImagesEqual(first, same));
        assertFalse(panel.bufferedImagesEqual(first, different));
    }


    private static String getLabelText(HudPanel panel, String fieldName) throws Exception {
        Field field = HudPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        JLabel label = (JLabel) field.get(panel);
        return label.getText();
    }

}
