/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Main.java to edit this template
 */
package monitor.de.aire;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import static javax.swing.WindowConstants.EXIT_ON_CLOSE;

public class MonitorDeAire extends JFrame {

    // ── Paleta ────────────────────────────────────────────────────────────
    static final Color BG = new Color(0x08, 0x0E, 0x14);
    static final Color PANEL = new Color(0x10, 0x18, 0x22);
    static final Color BORDER = new Color(0x1C, 0x2C, 0x3C);
    static final Color CYAN = new Color(0x00, 0xD4, 0xFF);
    static final Color GREEN = new Color(0x00, 0xFF, 0x9D);
    static final Color AMBER = new Color(0xFF, 0xAA, 0x00);
    static final Color RED = new Color(0xFF, 0x33, 0x44);
    static final Color TEXT = new Color(0xDD, 0xEE, 0xFF);
    static final Color DIM = new Color(0x55, 0x77, 0x99);

    // ── Límites NO₂ (OMS) ─────────────────────────────────────────────────
    static final double MAX = 2.0;
    static final double THR_WARN = 0.20;
    static final double THR_DANGER = 0.60;

    // ── Estado ────────────────────────────────────────────────────────────
    private double target = 0.0;
    private double current = 0.0;
    private final List<Double> history = new ArrayList<>();
    private static final int HIST = 50;
    private boolean connected = false;
    private boolean demoOn = false;

    // ── Componentes ───────────────────────────────────────────────────────
    private GaugeDial gauge;
    private MiniGraph graph;
    private JLabel lblValue, lblUnit, lblStatus, lblLevel, lblTime;
    private JComboBox<String> cmbPort;
    private JButton btnConnect, btnDemo;
    private Timer animTimer, demoTimer;
    private Object serialPort; // jSerialComm ref dinámica

    // ─────────────────────────────────────────────────────────────────────
    public MonitorDeAire() {
        super("NO₂ Monitor — Prototipo v1.0");
        setup();
        buildUI();
        startAnim();
        setValue(0.0);
    }

    private void setup() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setResizable(false);
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout());
        // Icono
        BufferedImage ico = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = ico.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(CYAN);
        g.fillOval(2, 2, 28, 28);
        g.setColor(BG);
        g.setFont(new Font("Monospaced", Font.BOLD, 14));
        g.drawString("N", 9, 21);
        g.dispose();
        setIconImage(ico);
    }

    // ─────────────────────────────────────────────────────────────────────
    private void buildUI() {
        // ── Header ────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(12, 20, 12, 20)
        ));

        JPanel hLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        hLeft.setOpaque(false);
        JLabel dot = lbl("◉ ", CYAN, 16, Font.PLAIN);
        JLabel title = lbl("NO₂  MONITOR", TEXT, 18, Font.BOLD);
        title.setFont(new Font("Monospaced", Font.BOLD, 18));
        JLabel ver = lbl(" · v1.0", DIM, 12, Font.PLAIN);
        hLeft.add(dot);
        hLeft.add(title);
        hLeft.add(ver);

        lblTime = lbl("--:--:--", DIM, 12, Font.PLAIN);
        lblStatus = lbl("● DESCONECTADO", RED, 12, Font.BOLD);

        JPanel hRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        hRight.setOpaque(false);
        hRight.add(lblTime);
        hRight.add(lblStatus);

        header.add(hLeft, BorderLayout.WEST);
        header.add(hRight, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ── Centro ────────────────────────────────────────────────────────
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.X_AXIS));
        center.setBackground(BG);
        center.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        // Gauge izquierda
        gauge = new GaugeDial();
        gauge.setPreferredSize(new Dimension(300, 300));
        gauge.setMinimumSize(new Dimension(300, 300));
        gauge.setMaximumSize(new Dimension(300, 300));

        // Panel derecho: valor + graph + info
        JPanel right = new JPanel();
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        right.setOpaque(false);
        right.setBorder(BorderFactory.createEmptyBorder(10, 20, 0, 0));

        // Valor grande
        lblValue = new JLabel("0.000");
        lblValue.setFont(new Font("Monospaced", Font.BOLD, 52));
        lblValue.setForeground(CYAN);
        lblValue.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblUnit = lbl("ppm  —  NO₂", DIM, 13, Font.PLAIN);
        lblUnit.setAlignmentX(Component.LEFT_ALIGNMENT);

        lblLevel = lbl("✓  NIVEL NORMAL", GREEN, 13, Font.BOLD);
        lblLevel.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Mini tabla de referencia OMS
        JPanel refTable = buildRefTable();
        refTable.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Gráfica historial
        JLabel gTitle = lbl("HISTORIAL  (últimas " + HIST + " lecturas)", DIM, 10, Font.PLAIN);
        gTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        graph = new MiniGraph();
        graph.setPreferredSize(new Dimension(300, 90));
        graph.setMinimumSize(new Dimension(300, 90));
        graph.setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
        graph.setAlignmentX(Component.LEFT_ALIGNMENT);

        right.add(Box.createVerticalStrut(4));
        right.add(lblValue);
        right.add(Box.createVerticalStrut(2));
        right.add(lblUnit);
        right.add(Box.createVerticalStrut(8));
        right.add(lblLevel);
        right.add(Box.createVerticalStrut(16));
        right.add(refTable);
        right.add(Box.createVerticalStrut(16));
        right.add(gTitle);
        right.add(Box.createVerticalStrut(4));
        right.add(graph);

        center.add(gauge);
        center.add(right);
        add(center, BorderLayout.CENTER);

        // ── Footer serial ─────────────────────────────────────────────────
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 8));
        footer.setBackground(PANEL);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

        footer.add(lbl("Puerto:", DIM, 11, Font.PLAIN));
        cmbPort = new JComboBox<>(getPorts());
        styleCombo(cmbPort);
        footer.add(cmbPort);

        JButton btnRefresh = btn("↻", CYAN);
        btnRefresh.addActionListener(e -> {
            cmbPort.removeAllItems();
            for (String p : getPorts()) {
                cmbPort.addItem(p);
            }
        });
        footer.add(btnRefresh);
        footer.add(Box.createHorizontalStrut(6));

        btnConnect = btn("CONECTAR", GREEN);
        btnConnect.addActionListener(this::onConnect);
        footer.add(btnConnect);

        btnDemo = btn("DEMO", AMBER);
        btnDemo.addActionListener(this::onDemo);
        footer.add(btnDemo);

        footer.add(Box.createHorizontalStrut(12));
        footer.add(lbl("115200 baud  ·  NO2:x.xxx\\n", DIM, 10, Font.PLAIN));

        add(footer, BorderLayout.SOUTH);

        pack();
        setMinimumSize(getSize());
    }

    // ── Tabla de referencia OMS ───────────────────────────────────────────
    private JPanel buildRefTable() {
        JPanel p = new JPanel(new GridLayout(4, 2, 6, 3));
        p.setBackground(new Color(0x0C, 0x14, 0x1E));
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        Object[][] rows = {
            {"REFERENCIA OMS", DIM},
            {"< 0.20 ppm  →  Normal", GREEN},
            {"0.20–0.60   →  Precaución", AMBER},
            {"> 0.60 ppm  →  Peligro", RED},};
        for (Object[] r : rows) {
            JLabel l = lbl((String) r[0], (Color) r[1], 11, Font.PLAIN);
            JLabel e = lbl("", DIM, 11, Font.PLAIN); // celda vacía 2ª col
            p.add(l);
            p.add(e);
        }
        // Reemplaza el layout por 1 columna
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.removeAll();
        for (Object[] r : rows) {
            JLabel l = lbl((String) r[0], (Color) r[1], 11, Font.PLAIN);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(l);
            p.add(Box.createVerticalStrut(2));
        }
        return p;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Actualizar valor
    // ─────────────────────────────────────────────────────────────────────
    private void setValue(double v) {
        target = Math.max(0, Math.min(MAX, v));

        // Historial
        history.add(target);
        if (history.size() > HIST) {
            history.remove(0);
        }

        // Etiqueta de nivel
        Color c;
        String txt;
        if (target > THR_DANGER) {
            c = RED;
            txt = "⚠  NIVEL PELIGROSO";
        } else if (target > THR_WARN) {
            c = AMBER;
            txt = "△  PRECAUCIÓN";
        } else {
            c = GREEN;
            txt = "✓  NIVEL NORMAL";
        }
        lblLevel.setForeground(c);
        lblLevel.setText(txt);

        // Timestamp
        java.time.LocalTime t = java.time.LocalTime.now();
        lblTime.setText(String.format("%02d:%02d:%02d", t.getHour(), t.getMinute(), t.getSecond()));

        graph.repaint();
    }

    // ── Animación suave ───────────────────────────────────────────────────
    private void startAnim() {
        animTimer = new Timer(15, e -> {
            current += (target - current) * 0.06;

            // Actualizar color del valor según nivel
            Color vc = current > THR_DANGER ? RED
                    : current > THR_WARN ? AMBER : CYAN;
            lblValue.setForeground(vc);
            lblValue.setText(String.format("%.3f", current));

            gauge.setVal(current);
        });
        animTimer.start();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Conexión serial
    // ─────────────────────────────────────────────────────────────────────
    private void onConnect(ActionEvent e) {
        if (!connected) {
            String port = (String) cmbPort.getSelectedItem();
            if (port == null || port.startsWith("(")) {
                JOptionPane.showMessageDialog(this,
                        "Selecciona un puerto válido.\n"
                        + "Asegúrate de que la ESP32-S3 esté conectada\n"
                        + "y que jSerialComm esté en el classpath.",
                        "Sin puerto", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (openSerial(port)) {
                connected = true;
                lblStatus.setText("● CONECTADO  " + port);
                lblStatus.setForeground(GREEN);
            } else {
                JOptionPane.showMessageDialog(this,
                        "No se pudo abrir " + port + ".\n"
                        + "Verifica que jSerialComm esté instalado.",
                        "Error Serial", JOptionPane.ERROR_MESSAGE);
            }
        } else {
            closeSerial();
            connected = false;
            lblStatus.setText("● DESCONECTADO");
            lblStatus.setForeground(RED);
        }
    }

    private void onDemo(ActionEvent e) {
        if (!demoOn) {
            demoOn = true;
            btnDemo.setForeground(AMBER.brighter());
            lblStatus.setText("● DEMO");
            lblStatus.setForeground(AMBER);
            double[] simVal = {0.0};
            demoTimer = new Timer(800, ev -> {
                simVal[0] = Math.max(0, Math.min(MAX, simVal[0] + (Math.random() - 0.46) * 0.12));
                SwingUtilities.invokeLater(() -> setValue(simVal[0]));
            });
            demoTimer.start();
        } else {
            demoOn = false;
            if (demoTimer != null) {
                demoTimer.stop();
            }
            if (!connected) {
                lblStatus.setText("● DESCONECTADO");
                lblStatus.setForeground(RED);
            }
        }
    }

    // ── jSerialComm (reflexión dinámica) ──────────────────────────────────
    private Thread serialThread;
    private volatile boolean serialRunning = false;

    private boolean openSerial(String portName) {
        try {
            Class<?> cls = Class.forName("com.fazecast.jSerialComm.SerialPort");
            serialPort = cls.getMethod("getCommPort", String.class).invoke(null, portName);
            cls.getMethod("setBaudRate", int.class).invoke(serialPort, 115200);
            boolean ok = (boolean) cls.getMethod("openPort").invoke(serialPort);
            if (!ok) {
                return false;
            }
            InputStream is = (InputStream) cls.getMethod("getInputStream").invoke(serialPort);
            serialRunning = true;
            serialThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while (serialRunning && (line = br.readLine()) != null) {
                        parseLine(line.trim());
                    }
                } catch (Exception ex) {
                    /* puerto cerrado */ }
            }, "serial-no2");
            serialThread.setDaemon(true);
            serialThread.start();
            return true;
        } catch (ClassNotFoundException ex) {
            System.err.println("[Serial] jSerialComm no encontrado.");
            return false;
        } catch (Exception ex) {
            System.err.println("[Serial] " + ex.getMessage());
            return false;
        }
    }

    private void closeSerial() {
        serialRunning = false;
        try {
            if (serialPort != null) {
                serialPort.getClass().getMethod("closePort").invoke(serialPort);
            }
        } catch (Exception ignored) {
        }
    }

    private void parseLine(String line) {
        // Acepta: "NO2:0.050"  o  "0.050"
        try {
            String num = line.toUpperCase().startsWith("NO2:") ? line.substring(4) : line;
            double v = Double.parseDouble(num.trim());
            SwingUtilities.invokeLater(() -> setValue(v));
        } catch (NumberFormatException ignored) {
        }
    }

    private static String[] getPorts() {
        try {
            Class<?> cls = Class.forName("com.fazecast.jSerialComm.SerialPort");
            Object[] ports = (Object[]) cls.getMethod("getCommPorts").invoke(null);
            String[] names = new String[ports.length];
            for (int i = 0; i < ports.length; i++) {
                names[i] = (String) ports[i].getClass().getMethod("getSystemPortName").invoke(ports[i]);
            }
            return names.length > 0 ? names : new String[]{"(Sin puertos)"};
        } catch (Exception e) {
            return new String[]{"(jSerialComm no instalado)"};
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Helpers UI
    // ─────────────────────────────────────────────────────────────────────
    private JLabel lbl(String t, Color c, int sz, int style) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Monospaced", style, sz));
        l.setForeground(c);
        return l;
    }

    private JButton btn(String text, Color accent) {
        JButton b = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? accent.darker() : PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setFont(new Font("Monospaced", Font.BOLD, 11));
                g2.setColor(getModel().isRollover() ? accent.brighter() : accent);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2,
                        (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(text.length() > 2 ? 88 : 32, 26));
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void styleCombo(JComboBox<String> c) {
        c.setBackground(new Color(0x0C, 0x14, 0x1E));
        c.setForeground(TEXT);
        c.setFont(new Font("Monospaced", Font.PLAIN, 11));
        c.setPreferredSize(new Dimension(130, 26));
    }

    // ─────────────────────────────────────────────────────────────────────
    // INNER CLASS: GaugeDial
    // ─────────────────────────────────────────────────────────────────────
    class GaugeDial extends JPanel {

        private double val = 0;

        GaugeDial() {
            setOpaque(false);
        }

        void setVal(double v) {
            val = v;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int cx = w / 2, cy = h / 2 + 10;
            int r = Math.min(w, h) / 2 - 22;

            double frac = val / MAX;
            // Color activo
            Color arc = frac > THR_DANGER / MAX ? RED
                    : frac > THR_WARN / MAX ? AMBER : CYAN;

            // ── Track ─────────────────────────────────────────────────────
            g2.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(BORDER);
            g2.drawArc(cx - r, cy - r, r * 2, r * 2, 220, -260);

            // ── Glow ──────────────────────────────────────────────────────
            if (frac > 0.005) {
                int deg = (int) (-260 * frac);
                g2.setStroke(new BasicStroke(22, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(new Color(arc.getRed(), arc.getGreen(), arc.getBlue(), 40));
                g2.drawArc(cx - r, cy - r, r * 2, r * 2, 220, deg);
                g2.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.setColor(arc);
                g2.drawArc(cx - r, cy - r, r * 2, r * 2, 220, deg);
            }

            // ── Marcas de umbral ──────────────────────────────────────────
            drawTick(g2, cx, cy, r, THR_WARN / MAX, AMBER);
            drawTick(g2, cx, cy, r, THR_DANGER / MAX, RED);

            // ── Marcas de escala (0, 0.5, 1.0, 1.5, 2.0) ─────────────────
            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            for (int i = 0; i <= 4; i++) {
                double f = i / 4.0;
                double angle = Math.toRadians(220 - 260 * f);
                int mx1 = (int) (cx + (r - 2) * Math.cos(angle));
                int my1 = (int) (cy - (r - 2) * Math.sin(angle));
                int mx2 = (int) (cx + (r + 8) * Math.cos(angle));
                int my2 = (int) (cy - (r + 8) * Math.sin(angle));
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(DIM);
                g2.drawLine(mx1, my1, mx2, my2);
                // etiqueta
                String lbl = String.format("%.1f", f * MAX);
                int lx = (int) (cx + (r + 20) * Math.cos(angle));
                int ly = (int) (cy - (r + 20) * Math.sin(angle));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(lbl, lx - fm.stringWidth(lbl) / 2, ly + fm.getAscent() / 2);
            }

            // ── Aguja ─────────────────────────────────────────────────────
            double na = Math.toRadians(220 - 260 * frac);
            int nx = (int) (cx + (r - 18) * Math.cos(na));
            int ny = (int) (cy - (r - 18) * Math.sin(na));
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(arc);
            g2.drawLine(cx, cy, nx, ny);
            // Centro
            g2.setColor(TEXT);
            g2.fillOval(cx - 6, cy - 6, 12, 12);
            g2.setColor(arc);
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(cx - 6, cy - 6, 12, 12);

            // ── Etiqueta central ──────────────────────────────────────────
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.setColor(DIM);
            String lbl = "NO₂  [ppm]";
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(lbl, cx - fm.stringWidth(lbl) / 2, cy + r / 2 + 12);

            g2.dispose();
        }

        private void drawTick(Graphics2D g2, int cx, int cy, int r, double f, Color c) {
            double angle = Math.toRadians(220 - 260 * f);
            int x1 = (int) (cx + (r - 8) * Math.cos(angle));
            int y1 = (int) (cy - (r - 8) * Math.sin(angle));
            int x2 = (int) (cx + (r + 4) * Math.cos(angle));
            int y2 = (int) (cy - (r + 4) * Math.sin(angle));
            g2.setStroke(new BasicStroke(2));
            g2.setColor(c);
            g2.drawLine(x1, y1, x2, y2);
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // INNER CLASS: MiniGraph
    // ─────────────────────────────────────────────────────────────────────
    class MiniGraph extends JPanel {

        MiniGraph() {
            setBackground(new Color(0x0C, 0x14, 0x1E));
            setBorder(BorderFactory.createLineBorder(BORDER, 1));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (history.size() < 2) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            int pl = 6, pr = 6, pt = 6, pb = 6;
            int pw = w - pl - pr, ph = h - pt - pb;
            int n = history.size();

            // Líneas de umbral
            int wy = pt + ph - (int) (ph * THR_WARN / MAX);
            int dy = pt + ph - (int) (ph * THR_DANGER / MAX);
            g2.setStroke(new BasicStroke(0.7f, BasicStroke.CAP_BUTT,
                    BasicStroke.JOIN_MITER, 1, new float[]{4, 3}, 0));
            g2.setColor(new Color(AMBER.getRed(), AMBER.getGreen(), AMBER.getBlue(), 80));
            g2.drawLine(pl, wy, w - pr, wy);
            g2.setColor(new Color(RED.getRed(), RED.getGreen(), RED.getBlue(), 80));
            g2.drawLine(pl, dy, w - pr, dy);

            // Curva
            float[] px = new float[n];
            float[] py = new float[n];
            for (int i = 0; i < n; i++) {
                px[i] = pl + (float) i / (n - 1) * pw;
                py[i] = pt + ph - (float) (history.get(i) / MAX) * ph;
            }
            // Relleno
            Path2D fill = new Path2D.Float();
            fill.moveTo(px[0], pt + ph);
            fill.lineTo(px[0], py[0]);
            for (int i = 1; i < n; i++) {
                float cpx = (px[i - 1] + px[i]) / 2;
                fill.curveTo(cpx, py[i - 1], cpx, py[i], px[i], py[i]);
            }
            fill.lineTo(px[n - 1], pt + ph);
            fill.closePath();
            g2.setPaint(new GradientPaint(0, pt, new Color(CYAN.getRed(), CYAN.getGreen(), CYAN.getBlue(), 50),
                    0, pt + ph, new Color(CYAN.getRed(), CYAN.getGreen(), CYAN.getBlue(), 0)));
            g2.fill(fill);
            // Línea
            Path2D line = new Path2D.Float();
            line.moveTo(px[0], py[0]);
            for (int i = 1; i < n; i++) {
                float cpx = (px[i - 1] + px[i]) / 2;
                line.curveTo(cpx, py[i - 1], cpx, py[i], px[i], py[i]);
            }
            g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(CYAN);
            g2.draw(line);
            // Punto final
            g2.setColor(BG);
            g2.fillOval((int) px[n - 1] - 3, (int) py[n - 1] - 3, 6, 6);
            g2.setColor(CYAN);
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawOval((int) px[n - 1] - 3, (int) py[n - 1] - 3, 6, 6);

            g2.dispose();
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            MonitorDeAire app = new MonitorDeAire();
            app.setLocationRelativeTo(null);
            app.setVisible(true);
        });
    }
}
