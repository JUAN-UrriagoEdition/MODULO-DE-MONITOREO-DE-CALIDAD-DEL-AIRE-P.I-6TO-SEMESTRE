package airmonitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

/**
 * HttpReader — Polling HTTP al ESP32 cada INTERVALO_MS milisegundos.
 *
 * Consulta GET http://<ip>/datos y entrega la línea al callback.
 * El JSON del ESP32:
 *   {"co":12.5,"no2":0.031,"co2":415.0,"voc":0.0,"estado":"REPOSO","t":12345}
 * Se convierte internamente al formato que ya parsea AirMonitorGUI:
 *   CO:12.50,NO2:0.031,CO2:415.0,VOC:0.0
 */
public class HttpReader {

    private static final int INTERVALO_MS  = 500;   // polling cada 500 ms
    private static final int TIMEOUT_MS    = 2000;  // timeout de conexión

    private final String ip;
    private final Consumer<String> onData;
    private final Consumer<String> onError;

    private Thread pollThread;
    private volatile boolean running = false;

    public HttpReader(String ip, Consumer<String> onData, Consumer<String> onError) {
        this.ip      = ip;
        this.onData  = onData;
        this.onError = onError;
    }

    /** Inicia el hilo de polling. */
    public void start() {
        running = true;
        pollThread = new Thread(() -> {
            while (running) {
                try {
                    String url = "http://" + ip + "/datos";
                    HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setConnectTimeout(TIMEOUT_MS);
                    conn.setReadTimeout(TIMEOUT_MS);
                    conn.setRequestMethod("GET");

                    int code = conn.getResponseCode();
                    if (code == 200) {
                        BufferedReader br = new BufferedReader(
                                new InputStreamReader(conn.getInputStream()));
                        String body = br.readLine();
                        br.close();
                        if (body != null && !body.isBlank()) {
                            String linea = jsonAProtocolo(body.trim());
                            if (linea != null) onData.accept(linea);
                        }
                    } else {
                        onError.accept("HTTP " + code);
                    }
                    conn.disconnect();

                } catch (Exception e) {
                    onError.accept(e.getMessage());
                }

                try { Thread.sleep(INTERVALO_MS); }
                catch (InterruptedException ex) { break; }
            }
        }, "http-reader");
        pollThread.setDaemon(true);
        pollThread.start();
    }

    /** Detiene el hilo de polling. */
    public void stop() {
        running = false;
        if (pollThread != null) pollThread.interrupt();
    }

    /**
     * Convierte el JSON del ESP32 al protocolo de la GUI:
     *   {"co":12.5,"no2":0.03,"co2":415.0,"voc":0.0,...}
     *   → "CO:12.50,NO2:0.031,CO2:415.0,VOC:0.0"
     */
    private String jsonAProtocolo(String json) {
        try {
            double co  = extraerDouble(json, "co");
            double no2 = extraerDouble(json, "no2");
            double co2 = extraerDouble(json, "co2");
            double voc = extraerDouble(json, "voc");
            return String.format("CO:%.2f,NO2:%.3f,CO2:%.1f,VOC:%.1f",
                                 co, no2, co2, voc);
        } catch (Exception e) {
            return null;
        }
    }

    /** Extrae un valor double de un JSON simple por nombre de clave. */
    private double extraerDouble(String json, String key) {
        // Busca "key": valor
        String patron = "\"" + key + "\":";
        int idx = json.indexOf(patron);
        if (idx < 0) return 0.0;
        int start = idx + patron.length();
        int end   = start;
        while (end < json.length() &&
               (Character.isDigit(json.charAt(end)) ||
                json.charAt(end) == '.' ||
                json.charAt(end) == '-')) {
            end++;
        }
        return Double.parseDouble(json.substring(start, end));
    }

    /** Retorna la IP configurada. */
    public String getIp() { return ip; }
}
