package group7.capstone.visuals.GUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;

public class SimulatorFrame extends JFrame {

    private final ImagePanel imagePanel;
    private final HudPanel hudPanel;
    private final JLabel footerLabel;

    public SimulatorFrame() {
        super("Capstone Simulator");

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        getContentPane().setBackground(UITheme.BG);

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(UITheme.PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
                new EmptyBorder(10, 12, 10, 12)
        ));

        JLabel title = new JLabel("Street View Simulator (MVP)");
        title.setFont(UITheme.TITLE_FONT);
        title.setForeground(UITheme.TEXT);

        header.add(title, BorderLayout.WEST);

        // center image
        imagePanel = new ImagePanel();

        // right HUD
        hudPanel = new HudPanel();

        // footer status bar
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(UITheme.PANEL);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UITheme.BORDER),
                new EmptyBorder(8, 12, 8, 12)
        ));

        footerLabel = new JLabel("Ready");
        footerLabel.setFont(UITheme.LABEL_FONT);
        footerLabel.setForeground(UITheme.MUTED);

        footer.add(footerLabel, BorderLayout.WEST);

        add(header, BorderLayout.NORTH);
        add(imagePanel, BorderLayout.CENTER);
        add(hudPanel, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);

        setSize(1280, 720);
        setLocationRelativeTo(null);
    }

    public void setStreetViewImage(BufferedImage img) {
        imagePanel.setImage(img);
    }

    public HudPanel getHud() {
        return hudPanel;
    }

    public void setFooterStatus(String text) {
        footerLabel.setText(text);
    }
}
