package group7.capstone.visuals.GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {

    private BufferedImage image;
    private BufferedImage prevImage;

    // Simple cross-fade so image swaps don't feel like "jumps"
    private float fadeAlpha = 1f; // 0..1 (new image)
    private javax.swing.Timer fadeTimer;

    public ImagePanel() {
        setBackground(UITheme.BG);
    }

    public void setImage(BufferedImage img) {
        if (img == null) {
            image = null;
            prevImage = null;
            fadeAlpha = 1f;
            repaint();
            return;
        }

        // If this is the first image, just set it.
        if (image == null) {
            image = img;
            prevImage = null;
            fadeAlpha = 1f;
            repaint();
            return;
        }

        // Start fade from old -> new.
        prevImage = image;
        image = img;
        fadeAlpha = 0f;

        if (fadeTimer != null && fadeTimer.isRunning()) {
            fadeTimer.stop();
        }

        final int durationMs = 220;
        final int tickMs = 16; // ~60fps
        final float step = (float) tickMs / (float) durationMs;

        fadeTimer = new javax.swing.Timer(tickMs, e -> {
            fadeAlpha = Math.min(1f, fadeAlpha + step);
            repaint();

            if (fadeAlpha >= 1f) {
                ((javax.swing.Timer) e.getSource()).stop();
                prevImage = null;
            }
        });
        fadeTimer.start();
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

        Graphics2D g2 = (Graphics2D) g.create();

        // Draw previous image underneath
        if (prevImage != null && fadeAlpha < 1f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2.drawImage(prevImage, x, y, drawW, drawH, null);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
            g2.drawImage(image, x, y, drawW, drawH, null);
        } else {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2.drawImage(image, x, y, drawW, drawH, null);
        }

        g2.dispose();

        // Light frame border
        g.setColor(UITheme.BORDER);
        g.drawRect(x, y, drawW - 1, drawH - 1);
    }
}
