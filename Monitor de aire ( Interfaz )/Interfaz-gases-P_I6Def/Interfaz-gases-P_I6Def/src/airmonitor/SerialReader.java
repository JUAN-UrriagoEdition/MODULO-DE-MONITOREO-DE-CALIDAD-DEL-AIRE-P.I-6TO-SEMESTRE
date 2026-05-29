package airmonitor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

/**
 * SerialReader — Hilo de lectura serial para ESP32-S3
 *
 * Requiere jSerialComm en lib/jSerialComm-2.11.x.jar
 * Project Properties → Libraries → Add JAR/Folder → lib/jSerialComm-2.11.x.jar
 *
 * Protocolo ESP32-S3 (115200 baud, \n):
 *   CO:<ppm>,NO2:<ppm>,CO2:<ppm>,VOC:<ppb>
 * Líneas que empiezan con '#' son ignoradas (log de debug).
 */
public class SerialReader {

    private final String portName;
    private final int baudRate;
    private final Consumer<String> onData;

    private Object serialPort; // com.fazecast.jSerialComm.SerialPort
    private Thread readerThread;
    private volatile boolean running = false;

    // Constantes de modo de timeout de jSerialComm
    // TIMEOUT_NONBLOCKING=0, TIMEOUT_READ_BLOCKING=16, TIMEOUT_WRITE_BLOCKING=2
    private static final int TIMEOUT_READ_BLOCKING = 16;

    public SerialReader(String portName, int baudRate, Consumer<String> onData) {
        this.portName = portName;
        this.baudRate = baudRate;
        this.onData   = onData;
    }

    /**
     * Abre el puerto serial e inicia el hilo de lectura.
     * @return true si fue exitoso
     */
    public boolean open() {
        try {
            Class<?> cls = Class.forName("com.fazecast.jSerialComm.SerialPort");

            // Obtener instancia del puerto
            serialPort = cls.getMethod("getCommPort", String.class)
                           .invoke(null, portName);

            // Configurar baud rate
            cls.getMethod("setBaudRate", int.class).invoke(serialPort, baudRate);

            // Abrir puerto
            boolean opened = (boolean) cls.getMethod("openPort").invoke(serialPort);
            if (!opened) {
                System.err.println("[Serial] No se pudo abrir " + portName);
                return false;
            }

            // Modo BLOCKING sin timeout → espera indefinida hasta recibir datos
            // Evita el error "read operation timed out"
            cls.getMethod("setComPortTimeouts", int.class, int.class, int.class)
               .invoke(serialPort, TIMEOUT_READ_BLOCKING, 0, 0);

            // InputStream del puerto
            java.io.InputStream is = (java.io.InputStream)
                cls.getMethod("getInputStream").invoke(serialPort);

            running = true;
            readerThread = new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while (running && (line = br.readLine()) != null) {
                        if (!line.isBlank()) onData.accept(line);
                    }
                } catch (Exception e) {
                    if (running) System.err.println("[Serial] Error lectura: " + e.getMessage());
                }
            }, "serial-reader");
            readerThread.setDaemon(true);
            readerThread.start();
            return true;

        } catch (ClassNotFoundException e) {
            System.err.println("[Serial] jSerialComm no encontrado en classpath.");
            System.err.println("  Descarga de: https://github.com/Fazecast/jSerialComm/releases");
            return false;
        } catch (Exception e) {
            System.err.println("[Serial] Error al abrir puerto: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cierra el puerto serial y detiene el hilo de lectura.
     */
    public void close() {
        running = false;
        try {
            if (serialPort != null) {
                serialPort.getClass().getMethod("closePort").invoke(serialPort);
            }
        } catch (Exception ignored) {}
        if (readerThread != null) readerThread.interrupt();
    }

    /**
     * Lista los puertos COM disponibles en el sistema.
     */
    public static String[] getAvailablePorts() {
        try {
            Class<?> cls = Class.forName("com.fazecast.jSerialComm.SerialPort");
            Object[] ports = (Object[]) cls.getMethod("getCommPorts").invoke(null);
            String[] names = new String[ports.length];
            for (int i = 0; i < ports.length; i++) {
                names[i] = (String) ports[i].getClass()
                    .getMethod("getSystemPortName").invoke(ports[i]);
            }
            return names.length > 0 ? names : new String[]{"(Sin puertos)"};
        } catch (ClassNotFoundException e) {
            return new String[]{"(jSerialComm no instalado)"};
        } catch (Exception e) {
            return new String[]{"(Error)"};
        }
    }
}