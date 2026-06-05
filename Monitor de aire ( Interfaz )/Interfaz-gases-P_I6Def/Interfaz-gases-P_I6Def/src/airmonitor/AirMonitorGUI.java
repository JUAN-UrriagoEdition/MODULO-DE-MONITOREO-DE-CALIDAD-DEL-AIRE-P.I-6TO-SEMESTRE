package airmonitor;
 
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
 
public class AirMonitorGUI extends JFrame {
 
    // ── Paleta dark industrial ────────────────────────────────────────────
    static final Color BG_DARK    = new Color(0x0D, 0x11, 0x17);
    static final Color BG_CARD    = new Color(0x13, 0x1A, 0x24);
    static final Color BG_CARD2   = new Color(0x18, 0x22, 0x30);
    static final Color ACCENT_CYAN   = new Color(0x00, 0xD9, 0xFF);
    static final Color ACCENT_GREEN  = new Color(0x00, 0xFF, 0x9D);
    static final Color ACCENT_AMBER  = new Color(0xFF, 0xB3, 0x00);
    static final Color ACCENT_RED    = new Color(0xFF, 0x3C, 0x3C);
    static final Color ACCENT_PURPLE = new Color(0xBB, 0x86, 0xFC);
    static final Color TEXT_MAIN  = new Color(0xE8, 0xF4, 0xFF);
    static final Color TEXT_DIM   = new Color(0x6A, 0x85, 0xA0);
    static final Color GRID_LINE  = new Color(0x1E, 0x2D, 0x3D);
 
    // ── Rangos y umbrales de cada gas ────────────────────────────────────
    // CO  : 0–1200 ppm  NIOSH TWA=35, STEL=200, IDLH=1200
    // NO2 : 0–10 ppm    OMS 0.2 anual; NIOSH STEL=1, IDLH=20
    // CO2 : 400–5000 ppm ASHRAE 1000, NIOSH 5000 TWA
    // VOC : 0–1000 ppb  IAQ <220 buena, <660 moderada
    static final double CO_MAX  = 1200.0;
    static final double NO2_MAX = 20.0;
    static final double CO2_MAX = 5000.0;
    static final double VOC_MAX = 1000.0;
 
    // Umbrales como fracción [precaución, peligro]
    static final double[] CO_THRESH  = {35.0/1200,  200.0/1200};
    static final double[] NO2_THRESH = {0.5/20.0,   1.0/20.0};
    static final double[] CO2_THRESH = {1000.0/5000, 2000.0/5000};
    static final double[] VOC_THRESH = {220.0/1000,  660.0/1000};
 
    // ── Valores actuales ─────────────────────────────────────────────────
    private double valCO  = 0.0;
    private double valNO2 = 0.0;
    private double valCO2 = 415.0;
    private double valVOC = 0.0;
    private boolean connected = false;
 
    // ── Historial (60 puntos) ─────────────────────────────────────────────
    private final List<Double> histCO  = new ArrayList<>();
    private final List<Double> histNO2 = new ArrayList<>();
    private final List<Double> histCO2 = new ArrayList<>();
    private final List<Double> histVOC = new ArrayList<>();
    private static final int HIST_MAX = 120;
 
    // ── Componentes UI ────────────────────────────────────────────────────
    private GaugePanel gaugeCO, gaugeNO2, gaugeCO2, gaugeVOC;
    private GraphPanel graphCO, graphNO2, graphCO2, graphVOC;
    private JLabel lblStatus, lblTimestamp;
    private javax.swing.JTextField txtIp;
    private JButton btnConnect;
    private HttpReader httpReader;
 
    // ── Animación lerp ────────────────────────────────────────────────────
    private double tgtCO=0, tgtNO2=0, tgtCO2=415, tgtVOC=0;
    private double curCO=0, curNO2=0, curCO2=415, curVOC=0;
    private Timer animTimer;
 
    public AirMonitorGUI() {
        super("AIR QUALITY MONITOR — P.I6Def | ESP32-S3");
        setupFrame();
        buildUI();
        startAnimationLoop();
        updateValues(0, 0, 415, 0);
    }
 
    private void setupFrame() {
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 760));
        setPreferredSize(new Dimension(1400, 860));
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout());
        BufferedImage icon = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = icon.createGraphics();
        g.setColor(ACCENT_GREEN); g.fillRoundRect(4,4,24,24,8,8); g.dispose();
        setIconImage(icon);
    }
 
    private void buildUI() {
        add(buildHeader(), BorderLayout.NORTH);
 
        JPanel center = new JPanel(new GridLayout(2, 4, 10, 10));
        center.setBackground(BG_DARK);
        center.setBorder(BorderFactory.createEmptyBorder(12, 14, 0, 14));
 
        // Gauges — colores por peligrosidad del gas
        gaugeCO  = new GaugePanel("CO",   "ppm", CO_MAX,  CO_THRESH,
                ACCENT_AMBER,  new Color(0xCC,0x80,0x00));
        gaugeNO2 = new GaugePanel("NO₂",  "ppm", NO2_MAX, NO2_THRESH,
                ACCENT_CYAN,   new Color(0x00,0x7A,0xFF));
        gaugeCO2 = new GaugePanel("CO₂",  "ppm", CO2_MAX, CO2_THRESH,
                ACCENT_GREEN,  new Color(0x00,0xAA,0x60));
        gaugeVOC = new GaugePanel("VOC",  "ppb", VOC_MAX, VOC_THRESH,
                ACCENT_PURPLE, new Color(0x80,0x50,0xCC));
 
        // Gráficas históricas
        graphCO  = new GraphPanel(histCO,  CO_MAX,  ACCENT_AMBER,  "CO — Monóxido de Carbono (ppm)");
        graphNO2 = new GraphPanel(histNO2, NO2_MAX, ACCENT_CYAN,   "NO₂ — Dióxido de Nitrógeno (ppm)");
        graphCO2 = new GraphPanel(histCO2, CO2_MAX, ACCENT_GREEN,  "CO₂ — Dióxido de Carbono (ppm)");
        graphVOC = new GraphPanel(histVOC, VOC_MAX, ACCENT_PURPLE, "VOC — Gases Volátiles (ppb)");
 
        center.add(gaugeCO);  center.add(gaugeNO2);
        center.add(gaugeCO2); center.add(gaugeVOC);
        center.add(graphCO);  center.add(graphNO2);
        center.add(graphCO2); center.add(graphVOC);
 
        add(center, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);
    }
 
    private JPanel buildHeader() {
        JPanel h = new JPanel(new BorderLayout());
        h.setBackground(BG_CARD);
        h.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0,0,1,0, ACCENT_CYAN.darker().darker()),
                BorderFactory.createEmptyBorder(14,20,14,20)));
 
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.setOpaque(false);
        JLabel dot = new JLabel("◉");
        dot.setFont(new Font("Monospaced", Font.PLAIN, 20));
        dot.setForeground(ACCENT_GREEN);
        JLabel title = new JLabel("AIR QUALITY MONITOR");
        title.setFont(new Font("Monospaced", Font.BOLD, 22));
        title.setForeground(TEXT_MAIN);
        JLabel sub = new JLabel("  ·  CO (GM-702B)  |  NO₂ (GM-102B)  |  CO₂ + VOC (CCS811)  |  ESP32-S3");
        sub.setFont(new Font("Monospaced", Font.PLAIN, 13));
        sub.setForeground(TEXT_DIM);
        left.add(dot); left.add(title); left.add(sub);
 
        lblStatus    = new JLabel("● DESCONECTADO");
        lblStatus.setFont(new Font("Monospaced", Font.BOLD, 13));
        lblStatus.setForeground(ACCENT_RED);
        lblTimestamp = new JLabel("--:--:--");
        lblTimestamp.setFont(new Font("Monospaced", Font.PLAIN, 12));
        lblTimestamp.setForeground(TEXT_DIM);
 
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 16, 0));
        right.setOpaque(false);
        right.add(lblTimestamp); right.add(lblStatus);
 
        h.add(left, BorderLayout.WEST);
        h.add(right, BorderLayout.EAST);
        return h;
    }
 
    private JPanel buildFooter() {
        JPanel f = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 10));
        f.setBackground(BG_CARD);
        f.setBorder(BorderFactory.createMatteBorder(1,0,0,0,
                ACCENT_CYAN.darker().darker()));

        JLabel lIp = makeLabel("IP del ESP32:", TEXT_DIM, 12);

        txtIp = new javax.swing.JTextField("192.168.1.100", 14);
        txtIp.setBackground(BG_CARD2);
        txtIp.setForeground(TEXT_MAIN);
        txtIp.setFont(new Font("Monospaced", Font.PLAIN, 12));
        txtIp.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT_CYAN.darker().darker(), 1),
            BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        txtIp.setCaretColor(ACCENT_CYAN);
        txtIp.addActionListener(this::onConnectToggle);

        btnConnect = makeButton("CONECTAR", ACCENT_GREEN);
        btnConnect.addActionListener(this::onConnectToggle);

        JButton btnDemo = makeButton("DEMO", ACCENT_AMBER);
        btnDemo.addActionListener(this::onDemoToggle);

        JLabel lProto = makeLabel(
            "Puerto: 80  |  Endpoint: /datos  |  Polling: 500ms  |  JSON",
            TEXT_DIM, 11);

        f.add(lIp); f.add(txtIp);
        f.add(Box.createHorizontalStrut(8));
        f.add(btnConnect); f.add(btnDemo);
        f.add(Box.createHorizontalStrut(16));
        f.add(lProto);
        return f;
    }
 
    // ─── Estilo ───────────────────────────────────────────────────────────
    private JLabel makeLabel(String t, Color c, int sz) {
        JLabel l = new JLabel(t);
        l.setFont(new Font("Monospaced", Font.PLAIN, sz));
        l.setForeground(c); return l;
    }
 
    private JButton makeButton(String text, Color accent) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? accent.darker() : BG_CARD2);
                g2.fillRoundRect(0,0,getWidth(),getHeight(),6,6);
                g2.setColor(accent);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0,0,getWidth()-1,getHeight()-1,6,6);
                g2.setColor(getModel().isRollover() ? accent.brighter() : accent);
                g2.setFont(new Font("Monospaced", Font.BOLD, 12));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text,
                        (getWidth()-fm.stringWidth(text))/2,
                        (getHeight()+fm.getAscent()-fm.getDescent())/2);
                g2.dispose();
            }
        };
        b.setPreferredSize(new Dimension(text.length()>4 ? 110 : 36, 30));
        b.setBorderPainted(false); b.setContentAreaFilled(false);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
 

 
    // ─── HTTP ────────────────────────────────────────────────────────────
    private void onConnectToggle(ActionEvent e) {
        if (!connected) {
            String ip = txtIp.getText().trim();
            if (ip.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Ingresa la IP del ESP32.", "Sin IP",
                        JOptionPane.WARNING_MESSAGE); return;
            }
            httpReader = new HttpReader(ip, this::onSerialData, err ->
                SwingUtilities.invokeLater(() -> {
                    lblStatus.setText("⚠ Sin conexión — " + err);
                    lblStatus.setForeground(ACCENT_AMBER);
                })
            );
            httpReader.start();
            connected = true;
            lblStatus.setText("● CONECTADO  " + ip);
            lblStatus.setForeground(ACCENT_GREEN);
            btnConnect.setText("DESCONECTAR");
        } else {
            if (httpReader != null) httpReader.stop();
            connected = false;
            lblStatus.setText("● DESCONECTADO");
            lblStatus.setForeground(ACCENT_RED);
            btnConnect.setText("CONECTAR");
        }
    }
 
    // ─── Demo ─────────────────────────────────────────────────────────────
    private Timer demoTimer;
    private boolean demoRunning = false;
 
    private void onDemoToggle(ActionEvent e) {
        if (!demoRunning) {
            demoRunning = true;
            demoTimer = new Timer(200, ev -> {
                // Simula datos realistas con drift lento
                double co  = Math.max(0, Math.min(CO_MAX,
                        valCO  + (Math.random()-0.47)*8));
                double no2 = Math.max(0, Math.min(NO2_MAX,
                        valNO2 + (Math.random()-0.47)*0.08));
                double co2 = Math.max(400, Math.min(CO2_MAX,
                        valCO2 + (Math.random()-0.45)*50));
                double voc = Math.max(0, Math.min(VOC_MAX,
                        valVOC + (Math.random()-0.46)*25));
                onSerialData(String.format("CO:%.2f,NO2:%.3f,CO2:%.1f,VOC:%.1f",
                        co, no2, co2, voc));
            });
            demoTimer.start();
            lblStatus.setText("● DEMO ACTIVO");
            lblStatus.setForeground(ACCENT_AMBER);
        } else {
            demoRunning = false;
            if (demoTimer != null) demoTimer.stop();
            if (!connected) {
                lblStatus.setText("● DESCONECTADO");
                lblStatus.setForeground(ACCENT_RED);
            }
        }
    }
 
    // ─── Parser serial ────────────────────────────────────────────────────
    public void onSerialData(String line) {
        if (line == null || line.isBlank() || line.startsWith("#")) return;
        try {
            double co=valCO, no2=valNO2, co2=valCO2, voc=valVOC;
            for (String part : line.trim().split(",")) {
                String[] kv = part.split(":");
                if (kv.length < 2) continue;
                switch (kv[0].trim().toUpperCase()) {
                    case "CO"  -> co  = Double.parseDouble(kv[1].trim());
                    case "NO2" -> no2 = Double.parseDouble(kv[1].trim());
                    case "CO2" -> co2 = Double.parseDouble(kv[1].trim());
                    case "VOC" -> voc = Double.parseDouble(kv[1].trim());
                }
            }
            final double fc=co, fn=no2, fco2=co2, fv=voc;
            SwingUtilities.invokeLater(() -> updateValues(fc, fn, fco2, fv));
        } catch (Exception ignored) {}
    }
 
    private void updateValues(double co, double no2, double co2, double voc) {
        valCO=co; valNO2=no2; valCO2=co2; valVOC=voc;
        tgtCO=co; tgtNO2=no2; tgtCO2=co2; tgtVOC=voc;
 
        addHistory(histCO,  co);
        addHistory(histNO2, no2);
        addHistory(histCO2, co2);
        addHistory(histVOC, voc);
 
        java.time.LocalTime t = java.time.LocalTime.now();
        lblTimestamp.setText(String.format("%02d:%02d:%02d",
                t.getHour(), t.getMinute(), t.getSecond()));
 
        graphCO.repaint(); graphNO2.repaint();
        graphCO2.repaint(); graphVOC.repaint();
    }
 
    private void addHistory(List<Double> list, double val) {
        list.add(val);
        if (list.size() > HIST_MAX) list.remove(0);
    }
 
    private void startAnimationLoop() {
        animTimer = new Timer(16, e -> {
            double spd = 0.25;
            curCO  += (tgtCO  - curCO)  * spd;
            curNO2 += (tgtNO2 - curNO2) * spd;
            curCO2 += (tgtCO2 - curCO2) * spd;
            curVOC += (tgtVOC - curVOC) * spd;
            gaugeCO.setValue(curCO);
            gaugeNO2.setValue(curNO2);
            gaugeCO2.setValue(curCO2);
            gaugeVOC.setValue(curVOC);
        });
        animTimer.start();
    }
 
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) {}
            AirMonitorGUI app = new AirMonitorGUI();
            app.pack();
            app.setLocationRelativeTo(null);
            app.setVisible(true);
        });
    }
}
 