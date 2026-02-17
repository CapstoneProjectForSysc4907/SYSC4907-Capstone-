package group7.capstone.visuals.GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {

    private BufferedImage image;

    public ImagePanel() {
        setBackground(UITheme.BG);
    }

    public void setImage(BufferedImage img) {
        image = img;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (image == null) {
            g.setColor(UITheme.TEXT);
            g.drawString("No image", 10, 20);
            return;
        }

        int panelW = getWidth();
        int panelH = getHeight();

        int imgW = image.getWidth();
        int imgH = image.getHeight();

        // Keep aspect ratio
        double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
        int drawW = (int) (imgW * scale);
        int drawH = (int) (imgH * scale);

        int x = (panelW - drawW) / 2;
        int y = (panelH - drawH) / 2;

        g.drawImage(image, x, y, drawW, drawH, null);

        // Light frame border
        g.setColor(UITheme.BORDER);
        g.drawRect(x, y, drawW - 1, drawH - 1);
    }
}
