package group7.capstone.visuals.GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.geom.AffineTransform;

public class ImagePanel extends JPanel {

    private BufferedImage image;
    private BufferedImage prevImage;

    private BufferedImage imgToCheck;

    // Simple cross-fade so image swaps don't feel like "jumps"
    private float fadeAlpha = 1f; // 0..1 (new image)
    private javax.swing.Timer fadeTimer;

    private double zoom;
    private static double zoomCoef = 0.0005;

    private double turning;
    private int lastHead = 500;
    private static double turnCoef = 0.0333;

    public ImagePanel() {
        setBackground(UITheme.BG);
        zoom = 1;
        turning = 2;
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
            imgToCheck = image;
            prevImage = null;
            fadeAlpha = 1f;
            repaint();
            return;
        }

        //checks if the new image is identical to the previous one before resetting zoom
        if (!bufferedImagesEqual(image,imgToCheck)) {
            zoom = 1;
            turning = 2;
        }
        imgToCheck = image;

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

        double zoomWidth = imgW * zoom;
        double zoomHeight = imgH * zoom;

        double anchorx = (imgW - zoomWidth);
        double anchory = (imgH - zoomHeight);

        AffineTransform at = new AffineTransform();
        at.scale(zoom, zoom);

        // Keep aspect ratio
        double scale = Math.min((double) panelW / imgW, (double) panelH / imgH);
        int drawW = (int) (imgW * scale);
        int drawH = (int) (imgH * scale);

        int x = 0;
        int y = 0;

        Graphics2D g2 = (Graphics2D) g.create();

        at.translate(0, 0);

        g2.setTransform(at);

        int cropX = (int)((imgW-(imgW/zoom))/turning);
        int cropY = (int)((imgH-(imgH/zoom))/2.2);

        if((cropX + (int)(imgW/zoom)) > imgW){
            cropX = imgW-(int)(imgW/zoom)-1;
        }
        if((cropY + (int)(imgH/zoom)) > imgH){
            cropY = imgH-(int)(imgH/zoom)-1;
        }

        BufferedImage croppedImage = image.getSubimage(cropX, cropY, (int)(imgW/zoom), (int)(imgH/zoom));

        // Draw previous image underneath
        if (prevImage != null && fadeAlpha < 1f) {
            BufferedImage croppedPrevImage = prevImage.getSubimage(cropX, cropY, (int)(imgW/zoom), (int)(imgH/zoom));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2.drawImage(croppedPrevImage, x, y, (int)(drawW/zoom), (int)(drawH/zoom), null);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha));
            g2.drawImage(croppedImage, x, y, (int)(drawW/zoom), (int)(drawH/zoom), null);
        } else {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2.drawImage(croppedImage, x, y, (int)(drawW/zoom), (int)(drawH/zoom), null);
        }

        g2.dispose();

        // Light frame border
        g.setColor(UITheme.BORDER);
        g.drawRect(x, y, drawW - 1, drawH - 1);
    }

    public void zoom(double dist, int turn){
        if(lastHead == 500){
            lastHead = turn;
        }

        zoom += dist*zoomCoef;
        if (zoom < 1.00) {
            zoom = 1.00;
        }


        turning = turning + ((lastHead-turn)*turnCoef)*(1/(zoom*zoom));

        if (turning < 0.1) {
            turning = 0.1;
        } else if (turning > 4) {
            turning = 4;
        }
        lastHead = turn;

        repaint();
    }

    boolean bufferedImagesEqual(BufferedImage img1, BufferedImage img2) {

        int targetWidth = 800;
        int targetHeight = 600;

        BufferedImage sImg1 = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        BufferedImage sImg2 = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);

        Graphics g1 = sImg1.createGraphics();
        g1.drawImage(img1, 0, 0, targetWidth, targetHeight, null);
        g1.dispose();

        Graphics g2 = sImg2.createGraphics();
        g2.drawImage(img2, 0, 0, targetWidth, targetHeight, null);
        g2.dispose();

        try {
            for (int x = 1; x < sImg1.getWidth(); x++) {
                for (int y = 1; y < sImg1.getHeight(); y++) {
                    //ensures that pixels are the same and that the second image isn't black
                    if (sImg1.getRGB(x, y) != sImg2.getRGB(x, y) && sImg2.getRGB(x,y) != -16777216) {
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
