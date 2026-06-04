package com.pi6def.gasmonitor.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.pi6def.gasmonitor.R;
import com.pi6def.gasmonitor.model.GasData;
import com.pi6def.gasmonitor.network.EspClient;
import com.pi6def.gasmonitor.service.AlertNotifier;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    // ── UI ─────────────────────────────────────────────────
    private TextView tvCO, tvNO2, tvCO2, tvVOC;
    private TextView tvEstado, tvIp, tvConexion;
    private LineChart chartCO, chartNO2, chartCO2, chartVOC;
    private Button btnConectar, btnDesconectar, btnConfig;  // Button, no View
    private View cardEstado;
    private View gaugeCO, gaugeNO2, gaugeCO2, gaugeVOC;

    // ── Datos ──────────────────────────────────────────────
    private EspClient client;
    private AlertNotifier notifier;
    private String ipEsp = "192.168.1.17";
    private int tickCount = 0;
    private static final int MAX_ENTRIES = 60;

    private LineDataSet dsCO, dsNO2, dsCO2, dsVOC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        setupCharts();
        setupButtons();

        client   = new EspClient();
        notifier = new AlertNotifier(this);

        pedirPermisoNotificaciones();
    }

    private void bindViews() {
        tvCO       = findViewById(R.id.tv_co);
        tvNO2      = findViewById(R.id.tv_no2);
        tvCO2      = findViewById(R.id.tv_co2);
        tvVOC      = findViewById(R.id.tv_voc);
        tvEstado   = findViewById(R.id.tv_estado);
        tvIp       = findViewById(R.id.tv_ip);
        tvConexion = findViewById(R.id.tv_conexion);
        cardEstado = findViewById(R.id.card_estado);
        btnConectar    = findViewById(R.id.btn_conectar);
        btnDesconectar = findViewById(R.id.btn_desconectar);
        btnConfig      = findViewById(R.id.btn_config);
        chartCO  = findViewById(R.id.chart_co);
        chartNO2 = findViewById(R.id.chart_no2);
        chartCO2 = findViewById(R.id.chart_co2);
        chartVOC = findViewById(R.id.chart_voc);
        gaugeCO  = findViewById(R.id.gauge_co);
        gaugeNO2 = findViewById(R.id.gauge_no2);
        gaugeCO2 = findViewById(R.id.gauge_co2);
        gaugeVOC = findViewById(R.id.gauge_voc);
    }

    private void setupButtons() {
        btnConectar.setOnClickListener(v -> conectar());
        btnDesconectar.setOnClickListener(v -> desconectar());
        btnConfig.setOnClickListener(v -> mostrarDialogoIP());
        btnDesconectar.setEnabled(false);
    }

    private void conectar() {
        tvConexion.setText("Conectando...");
        tvIp.setText("IP: " + ipEsp);
        client.start(ipEsp, new EspClient.Listener() {
            @Override public void onData(GasData data) { actualizarUI(data); }
            @Override public void onError(String msg)  { mostrarError(msg);  }
        });
        btnConectar.setEnabled(false);
        btnDesconectar.setEnabled(true);
    }

    private void desconectar() {
        client.stop();
        tvConexion.setText("Desconectado");
        btnConectar.setEnabled(true);
        btnDesconectar.setEnabled(false);
    }

    private void mostrarDialogoIP() {
        EditText et = new EditText(this);
        et.setText(ipEsp);
        et.setHint("192.168.1.xxx");
        new AlertDialog.Builder(this)
                .setTitle("IP del ESP32")
                .setMessage("Ingresa la IP que aparece en el Serial Monitor:")
                .setView(et)
                .setPositiveButton("Guardar", (d, w) -> {
                    String ip = et.getText().toString().trim();
                    if (!ip.isEmpty()) ipEsp = ip;
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void actualizarUI(GasData d) {
        tvConexion.setText("● Conectado");
        tvConexion.setTextColor(Color.parseColor("#00FF9D"));

        tvCO.setText( String.format("%.1f ppm",  d.co));
        tvNO2.setText(String.format("%.3f ppm",  d.no2));
        tvCO2.setText(String.format("%.0f ppm",  d.co2));
        tvVOC.setText(String.format("%.0f ppb",  d.voc));

        colorGauge(gaugeCO,  d.co,  GasData.CO_ADV,  GasData.CO_PEL);
        colorGauge(gaugeNO2, d.no2, GasData.NO2_ADV, GasData.NO2_PEL);
        colorGauge(gaugeCO2, d.co2, GasData.CO2_ADV, GasData.CO2_PEL);
        colorGauge(gaugeVOC, d.voc, GasData.VOC_ADV, GasData.VOC_PEL);

        String est = d.estado != null ? d.estado : "REPOSO";
        tvEstado.setText(est);
        int bgColor;
        switch (est) {
            case "ALARMA":      bgColor = Color.parseColor("#FF3C3C"); break;
            case "PELIGRO":     bgColor = Color.parseColor("#CC8000"); break;
            case "ADVERTENCIA": bgColor = Color.parseColor("#FFB300"); break;
            default:            bgColor = Color.parseColor("#00A060"); break;
        }
        cardEstado.setBackgroundColor(bgColor);

        agregarPunto(chartCO,  dsCO,  (float) d.co);
        agregarPunto(chartNO2, dsNO2, (float) d.no2);
        agregarPunto(chartCO2, dsCO2, (float) d.co2);
        agregarPunto(chartVOC, dsVOC, (float) d.voc);
        tickCount++;

        notifier.evaluar(d);
    }

    private void colorGauge(View gauge, double val, double adv, double pel) {
        int color;
        if      (val >= pel) color = Color.parseColor("#FF3C3C");
        else if (val >= adv) color = Color.parseColor("#FFB300");
        else                 color = Color.parseColor("#00D99D");
        gauge.setBackgroundColor(color);
    }

    private void mostrarError(String msg) {
        tvConexion.setText("⚠ Sin conexión");
        tvConexion.setTextColor(Color.parseColor("#FF3C3C"));
    }

    private void setupCharts() {
        dsCO  = setupChart(chartCO,  "CO (ppm)",  Color.parseColor("#FFB300"), (float)GasData.CO_MAX);
        dsNO2 = setupChart(chartNO2, "NO2 (ppm)", Color.parseColor("#00D9FF"), (float)GasData.NO2_MAX);
        dsCO2 = setupChart(chartCO2, "CO2 (ppm)", Color.parseColor("#00FF9D"), (float)GasData.CO2_MAX);
        dsVOC = setupChart(chartVOC, "VOC (ppb)", Color.parseColor("#BB86FC"), (float)GasData.VOC_MAX);
    }

    private LineDataSet setupChart(LineChart chart, String label, int color, float yMax) {
        chart.setBackgroundColor(Color.parseColor("#13212A"));
        chart.getDescription().setEnabled(false);
        chart.setTouchEnabled(false);
        chart.getLegend().setTextColor(Color.WHITE);
        chart.getLegend().setTextSize(10f);

        XAxis x = chart.getXAxis();
        x.setDrawLabels(false);
        x.setDrawGridLines(false);
        x.setPosition(XAxis.XAxisPosition.BOTTOM);

        YAxis y = chart.getAxisLeft();
        y.setTextColor(Color.WHITE);
        y.setGridColor(Color.parseColor("#1E2D3D"));
        y.setAxisMinimum(0f);
        y.setAxisMaximum(yMax);
        chart.getAxisRight().setEnabled(false);

        LineDataSet ds = new LineDataSet(new ArrayList<>(), label);
        ds.setColor(color);
        ds.setLineWidth(2f);
        ds.setDrawValues(false);
        ds.setDrawCircles(false);
        ds.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        ds.setFillColor(color);
        ds.setFillAlpha(30);
        ds.setDrawFilled(true);

        chart.setData(new LineData(ds));
        chart.invalidate();
        return ds;
    }

    private void agregarPunto(LineChart chart, LineDataSet ds, float val) {
        LineData data = chart.getData();
        // Corrección: removeFirst() no existe en MPAndroidChart v3
        // Se usa removeEntry(index, dataSetIndex) para eliminar el más antiguo
        if (data.getEntryCount() > MAX_ENTRIES) {
            data.removeEntry(0, 0);
        }
        data.addEntry(new Entry(tickCount, val), 0);
        data.notifyDataChanged();
        chart.notifyDataSetChanged();
        chart.setVisibleXRangeMaximum(MAX_ENTRIES);
        chart.moveViewToX(tickCount);
    }

    private void pedirPermisoNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (client != null) client.stop();
    }
}
