package io.github.aitestflow.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Native Swing components using the active IDE palette, without a web runtime. */
public final class WorkbenchStyle {
    private WorkbenchStyle() { }
    public static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }
    public static Color muted() { return color("Label.disabledForeground", Color.GRAY); }
    public static Color accent() { return color("Component.focusColor", new Color(73, 115, 218)); }
    public static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }
    public static JLabel label(String text, int size, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(bold ? Font.BOLD : Font.PLAIN, (float) size));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }
    public static JLabel hint(String text) {
        JLabel label = label(text, 12, false); label.setForeground(muted()); return label;
    }
    public static JPanel card(String title, String subtitle, JComponent content) {
        JPanel card = new JPanel(new BorderLayout(0, 12));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color("Component.borderColor", new Color(125, 130, 140))),
                new EmptyBorder(16, 16, 16, 16)));
        JPanel heading = column();
        heading.add(label(title, 14, true));
        if (subtitle != null && !subtitle.isBlank()) {
            heading.add(Box.createVerticalStrut(5)); heading.add(hint(subtitle));
        }
        card.add(heading, BorderLayout.NORTH); card.add(content, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }
    public static void field(JPanel parent, String label, JComponent field) {
        JLabel caption = label(label, 12, false);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(32, field.getPreferredSize().height)));
        caption.setLabelFor(field);
        parent.add(caption); parent.add(Box.createVerticalStrut(6)); parent.add(field);
        parent.add(Box.createVerticalStrut(14));
    }
    public static JButton action(String title, Runnable action) {
        JButton button = new JButton(title) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D painter = (Graphics2D) graphics.create();
                painter.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean primary = Boolean.TRUE.equals(getClientProperty("workbench.primary"));
                Color fill = primary ? accent() : color("Button.background", new Color(242, 244, 248));
                if (getModel().isRollover() && isEnabled()) fill = primary ? fill.darker() : fill.brighter();
                if (!isEnabled()) fill = color("Panel.background", new Color(235, 237, 241));
                painter.setColor(fill); painter.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                painter.setColor(isFocusOwner() ? accent() : color("Component.borderColor", new Color(190, 196, 207)));
                painter.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                painter.dispose();
                setForeground(primary && isEnabled() ? Color.WHITE : color("Label.foreground", Color.DARK_GRAY));
                super.paintComponent(graphics);
            }
        };
        button.setOpaque(false); button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        button.setFocusPainted(false); button.setRolloverEnabled(true);
        button.addActionListener(event -> action.run());
        return button;
    }
}
