package airmonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

import static airmonitor.AirMonitorGUI.*;

/*
 * GaugePanel - Medidor circular tipo arco con animación de colores y niveles de alerta configurables.
 */
public class GaugePanel extends JPanel {

    private final String label;
    private final String unit;
    private final double maxVal;
    private final double[] thresholds;
    private final Color colorNormal;
    private final Color colorGlow;

    private double value = 0.0;

    // Precalculados para el arco
    private static final int ARC_START  = 220;
    private static final int ARC_SWEEP  = -260;

    public GaugePanel(String label, String unit, double maxVal,
                      double[] thresholds, Color colorNormal, Color colorGlow) {
        this.label      = label;
        this.unit       = unit;
        this.maxVal     = maxVal;
        this.thresholds = thresholds;
        this.colorNormal = colorNormal;
        this.colorGlow   = colorGlow;

        setBackground(BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GRID_LINE, 1, true),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));
        setMinimumSize(new Dimension(220, 200));
    }

    public void setValue(double v) {
        this.value = Math.max(0, Math.min(maxVal, v));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int cx = w / 2;
        int cy = (int)(h * 0.52);
        int r  = Math.min(w, h * 2 / 3) / 2 - 12;

        double fraction = value / maxVal;

        // ── Color dinámico por nivel de alerta ────────────────────────────
        Color activeColor;
        Color glowColor;
        if (fraction >= thresholds[1]) {
            activeColor = ACCENT_RED;
            glowColor   = new Color(0xFF, 0x3C, 0x3C, 80);
        } else if (fraction >= thresholds[0]) {
            activeColor = ACCENT_AMBER;
            glowColor   = new Color(0xFF, 0xB3, 0x00, 60);
        } else {
            activeColor = colorNormal;
            glowColor   = new Color(colorNormal.getRed(), colorNormal.getGreen(),
                                    colorNormal.getBlue(), 50);
        }

        // ── Fondo del arco (track) ────────────────────────────────────────
        g2.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(GRID_LINE);
        g2.drawArc(cx - r, cy - r, r * 2, r * 2, ARC_START, ARC_SWEEP);

        // ── Glów / halo detrás del arco activo ───────────────────────────
        if (fraction > 0.01) {
            int arcDeg = (int)(ARC_SWEEP * fraction);
            g2.setStroke(new BasicStroke(18, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(glowColor);
            g2.drawArc(cx - r, cy - r, r * 2, r * 2, ARC_START, arcDeg);
        }

        // ── Arco activo ───────────────────────────────────────────────────
        if (fraction > 0.01) {
            int arcDeg = (int)(ARC_SWEEP * fraction);
            g2.setStroke(new BasicStroke(8, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            // Gradiente a lo largo del arco (aproximado con color sólido)
            g2.setColor(activeColor);
            g2.drawArc(cx - r, cy - r, r * 2, r * 2, ARC_START, arcDeg);
        }

        // ── Marcas de umbral ─────────────────────────────────────────────
        drawThresholdTick(g2, cx, cy, r, thresholds[0], ACCENT_AMBER);
        drawThresholdTick(g2, cx, cy, r, thresholds[1], ACCENT_RED);

        // ── Aguja ─────────────────────────────────────────────────────────
        double needleAngle = Math.toRadians(ARC_START + ARC_SWEEP * fraction);
        int nx = (int)(cx + (r - 20) * Math.cos(needleAngle));
        int ny = (int)(cy - (r - 20) * Math.sin(needleAngle)); // y invertido
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(activeColor);
        g2.drawLine(cx, cy, nx, ny);
        // Punto central
        g2.setColor(TEXT_MAIN);
        g2.fillOval(cx - 5, cy - 5, 10, 10);
        g2.setColor(activeColor);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawOval(cx - 5, cy - 5, 10, 10);

        // ── Valor numérico ────────────────────────────────────────────────
        String valStr = formatValue(value);
        g2.setFont(new Font("Monospaced", Font.BOLD, 28));
        FontMetrics fm = g2.getFontMetrics();
        int vx = cx - fm.stringWidth(valStr) / 2;
        int vy = cy + r / 2 - 8;
        // Sombra
        g2.setColor(new Color(0, 0, 0, 120));
        g2.drawString(valStr, vx + 1, vy + 1);
        g2.setColor(activeColor);
        g2.drawString(valStr, vx, vy);

        // Unidad
        g2.setFont(new Font("Monospaced", Font.PLAIN, 12));
        g2.setColor(TEXT_DIM);
        String uStr = unit;
        g2.drawString(uStr, cx - g2.getFontMetrics().stringWidth(uStr)/2, vy + 18);

        // ── Etiqueta del gas ──────────────────────────────────────────────
        g2.setFont(new Font("Monospaced", Font.BOLD, 16));
        g2.setColor(TEXT_MAIN);
        int lw = g2.getFontMetrics().stringWidth(label);
        g2.drawString(label, cx - lw/2, h - 14);

        // ── Nivel de alerta (texto) ───────────────────────────────────────
        String nivel; Color nColor;
        if (fraction >= thresholds[1])      { nivel = "⚠ PELIGRO";    nColor = ACCENT_RED;   }
        else if (fraction >= thresholds[0]) { nivel = "△ PRECAUCIÓN"; nColor = ACCENT_AMBER; }
        else                                { nivel = "✓ NORMAL";     nColor = ACCENT_GREEN; }
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(nColor);
        int nw = g2.getFontMetrics().stringWidth(nivel);
        g2.drawString(nivel, cx - nw/2, h - 2);

        g2.dispose();
    }

    private void drawThresholdTick(Graphics2D g2, int cx, int cy, int r,
                                   double frac, Color c) {
        double angle = Math.toRadians(ARC_START + ARC_SWEEP * frac);
        int x1 = (int)(cx + (r - 5)  * Math.cos(angle));
        int y1 = (int)(cy - (r - 5)  * Math.sin(angle));
        int x2 = (int)(cx + (r + 5)  * Math.cos(angle));
        int y2 = (int)(cy - (r + 5)  * Math.sin(angle));
        g2.setStroke(new BasicStroke(2));
        g2.setColor(c);
        g2.drawLine(x1, y1, x2, y2);
    }

    private String formatValue(double v) {
        if (maxVal >= 1000) return String.format("%.0f", v);
        if (maxVal >= 10)   return String.format("%.1f", v);
        return String.format("%.3f", v);
    }
}
