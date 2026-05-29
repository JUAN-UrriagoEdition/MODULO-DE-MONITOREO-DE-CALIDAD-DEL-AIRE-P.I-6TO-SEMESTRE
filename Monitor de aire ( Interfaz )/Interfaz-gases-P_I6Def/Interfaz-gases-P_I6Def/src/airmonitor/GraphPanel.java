package airmonitor;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

import static airmonitor.AirMonitorGUI.*;

/**
 * GraphPanel — Gráfica de línea para el historial de un gas.
 * Muestra los últimos N valores con relleno degradado bajo la curva.
 */
public class GraphPanel extends JPanel {

    private final List<Double> data;
    private final double maxVal;
    private final Color lineColor;
    private final String title;

    private static final int PAD_L = 44;
    private static final int PAD_R = 10;
    private static final int PAD_T = 24;
    private static final int PAD_B = 24;

    public GraphPanel(List<Double> data, double maxVal, Color lineColor, String title) {
        this.data      = data;
        this.maxVal    = maxVal;
        this.lineColor = lineColor;
        this.title     = title;

        setBackground(BG_CARD);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(GRID_LINE, 1, true),
            BorderFactory.createEmptyBorder(4, 4, 4, 4)
        ));
        setMinimumSize(new Dimension(200, 140));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (data.isEmpty()) return;

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth(), h = getHeight();
        int plotW = w - PAD_L - PAD_R;
        int plotH = h - PAD_T - PAD_B;

        // ── Título ────────────────────────────────────────────────────────
        g2.setFont(new Font("Monospaced", Font.PLAIN, 10));
        g2.setColor(TEXT_DIM);
        g2.drawString(title, PAD_L, 14);

        // ── Último valor ──────────────────────────────────────────────────
        double last = data.get(data.size() - 1);
        String lastStr = formatValue(last);
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.setColor(lineColor);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(lastStr, w - PAD_R - fm.stringWidth(lastStr), 14);

        // ── Grid horizontal ───────────────────────────────────────────────
        g2.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER, 1, new float[]{3, 4}, 0));
        int GRID_ROWS = 4;
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(TEXT_DIM);
        for (int i = 0; i <= GRID_ROWS; i++) {
            double frac = (double) i / GRID_ROWS;
            int gy = PAD_T + plotH - (int)(plotH * frac);
            g2.setColor(GRID_LINE);
            g2.drawLine(PAD_L, gy, w - PAD_R, gy);
            // Etiqueta eje Y
            String yLbl = formatValue(maxVal * frac);
            g2.setColor(TEXT_DIM);
            FontMetrics yfm = g2.getFontMetrics();
            g2.drawString(yLbl, PAD_L - yfm.stringWidth(yLbl) - 3, gy + 4);
        }

        // ── Construir la path de la curva ─────────────────────────────────
        int n = data.size();
        float[] px = new float[n];
        float[] py = new float[n];
        for (int i = 0; i < n; i++) {
            px[i] = PAD_L + (float) i / Math.max(n - 1, 1) * plotW;
            py[i] = PAD_T + plotH - (float)(data.get(i) / maxVal) * plotH;
        }

        // Relleno degradado bajo la curva
        Path2D fillPath = new Path2D.Float();
        fillPath.moveTo(px[0], PAD_T + plotH);
        fillPath.lineTo(px[0], py[0]);
        for (int i = 1; i < n; i++) {
            // Curva suave con puntos de control
            float cpx1 = px[i-1] + (px[i]-px[i-1])*0.5f;
            float cpy1 = py[i-1];
            float cpx2 = px[i-1] + (px[i]-px[i-1])*0.5f;
            float cpy2 = py[i];
            fillPath.curveTo(cpx1, cpy1, cpx2, cpy2, px[i], py[i]);
        }
        fillPath.lineTo(px[n-1], PAD_T + plotH);
        fillPath.closePath();

        GradientPaint gp = new GradientPaint(
            0, PAD_T,      new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 60),
            0, PAD_T+plotH, new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), 0)
        );
        g2.setPaint(gp);
        g2.fill(fillPath);

        // Línea principal
        Path2D linePath = new Path2D.Float();
        linePath.moveTo(px[0], py[0]);
        for (int i = 1; i < n; i++) {
            float cpx1 = px[i-1] + (px[i]-px[i-1])*0.5f;
            float cpy1 = py[i-1];
            float cpx2 = px[i-1] + (px[i]-px[i-1])*0.5f;
            float cpy2 = py[i];
            linePath.curveTo(cpx1, cpy1, cpx2, cpy2, px[i], py[i]);
        }
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setPaint(lineColor);
        g2.draw(linePath);

        // Punto en el último valor
        if (n > 0) {
            g2.setColor(BG_CARD);
            g2.fillOval((int)px[n-1]-4, (int)py[n-1]-4, 8, 8);
            g2.setColor(lineColor);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval((int)px[n-1]-4, (int)py[n-1]-4, 8, 8);
        }

        g2.dispose();
    }

    private String formatValue(double v) {
        if (maxVal >= 1000) return String.format("%.0f", v);
        if (maxVal >= 10)   return String.format("%.1f", v);
        return String.format("%.3f", v);
    }
}
