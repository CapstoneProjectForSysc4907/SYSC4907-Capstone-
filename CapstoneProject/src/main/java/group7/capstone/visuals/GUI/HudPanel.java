package group7.capstone.visuals.GUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class HudPanel extends JPanel {

    private final JLabel speedValue = new JLabel("0 km/h");
    private final JLabel latLngValue = new JLabel("-");
    private final JLabel headingValue = new JLabel("-");
    private final JLabel statusValue = new JLabel("idle");

    public HudPanel() {
        setBackground(UITheme.PANEL);
        setPreferredSize(new Dimension(300, 600));
        setLayout(new BorderLayout());
        setBorder(new EmptyBorder(12, 12, 12, 12));

        JLabel title = new JLabel("HUD");
        title.setFont(UITheme.TITLE_FONT);
        title.setForeground(UITheme.TEXT);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new GridLayout(0, 1, 10, 10));

        content.add(makeCard("Speed", speedValue));
        content.add(makeCard("Lat / Lng", latLngValue));
        content.add(makeCard("Heading", headingValue));
        content.add(makeCard("Status", statusValue));

        add(title, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);

        // Styling values (student-simple)
        styleValue(speedValue);
        styleValue(latLngValue);
        styleValue(headingValue);
        styleValue(statusValue);
    }

    private void styleValue(JLabel label) {
        label.setFont(UITheme.VALUE_FONT);
        label.setForeground(UITheme.TEXT);
    }

    private JPanel makeCard(String labelText, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(UITheme.BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.BORDER, 1),
                new EmptyBorder(10, 10, 10, 10)
        ));

        JLabel label = new JLabel(labelText);
        label.setFont(UITheme.LABEL_FONT);
        label.setForeground(UITheme.MUTED);

        card.add(label, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    public void setSpeed(double kmh) {
        speedValue.setText(String.format("%.1f km/h", kmh));
    }

    public void setLatLng(double lat, double lng) {
        latLngValue.setText(lat + ", " + lng);
    }

    public void setHeading(int heading) {
        headingValue.setText(String.valueOf(heading));
    }

    public void setStatus(String status) {
        statusValue.setText(status);
    }
}
