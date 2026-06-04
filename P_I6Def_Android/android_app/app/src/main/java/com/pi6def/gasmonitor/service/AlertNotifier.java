package com.pi6def.gasmonitor.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.pi6def.gasmonitor.model.GasData;

public class AlertNotifier {

    private static final String CH_ADV   = "ch_advertencia";
    private static final String CH_PEL   = "ch_peligro";
    private static final String CH_ALARM = "ch_alarma";

    private static final int ID_ADV   = 1;
    private static final int ID_PEL   = 2;
    private static final int ID_ALARM = 3;

    private final Context ctx;
    private String lastEstado = "";

    public AlertNotifier(Context ctx) {
        this.ctx = ctx;
        createChannels();
    }

    private void createChannels() {
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);

        nm.createNotificationChannel(new NotificationChannel(
                CH_ADV, "Advertencia de gases",
                NotificationManager.IMPORTANCE_DEFAULT));

        NotificationChannel chPel = new NotificationChannel(
                CH_PEL, "Peligro de gases",
                NotificationManager.IMPORTANCE_HIGH);
        chPel.enableVibration(true);
        nm.createNotificationChannel(chPel);

        NotificationChannel chAlarm = new NotificationChannel(
                CH_ALARM, "ALARMA — Evacuación",
                NotificationManager.IMPORTANCE_HIGH);
        chAlarm.enableVibration(true);
        chAlarm.setVibrationPattern(new long[]{0,500,200,500,200,500});
        nm.createNotificationChannel(chAlarm);
    }

    public void evaluar(GasData data) {
        if (data.estado == null || data.estado.equals(lastEstado)) return;
        lastEstado = data.estado;

        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);

        switch (data.estado) {
            case "ADVERTENCIA":
                nm.notify(ID_ADV, new NotificationCompat.Builder(ctx, CH_ADV)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("⚠ Advertencia de gases")
                        .setContentText(buildResumen(data))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)
                        .build());
                break;

            case "PELIGRO":
                nm.cancel(ID_ADV);
                nm.notify(ID_PEL, new NotificationCompat.Builder(ctx, CH_PEL)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("🚨 Niveles de gas peligrosos")
                        .setContentText(buildResumen(data))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(false)
                        .build());
                break;

            case "ALARMA":
                nm.cancel(ID_ADV);
                nm.cancel(ID_PEL);
                nm.notify(ID_ALARM, new NotificationCompat.Builder(ctx, CH_ALARM)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("⛔ ALARMA — EVACUACIÓN")
                        .setContentText("Múltiples gases en niveles críticos. ¡Evacuar!")
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText("⛔ EVACUACIÓN INMEDIATA\n" + buildResumen(data)))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setAutoCancel(false)
                        .build());
                break;

            case "REPOSO":
                nm.cancel(ID_ADV);
                nm.cancel(ID_PEL);
                nm.cancel(ID_ALARM);
                break;
        }
    }

    private String buildResumen(GasData d) {
        return String.format("CO:%.1f | NO₂:%.2f | CO₂:%.0f | VOC:%.0f",
                d.co, d.no2, d.co2, d.voc);
    }
}
