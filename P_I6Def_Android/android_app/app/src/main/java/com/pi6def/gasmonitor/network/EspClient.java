package com.pi6def.gasmonitor.network;

import android.os.Handler;
import android.os.Looper;
import com.google.gson.Gson;
import com.pi6def.gasmonitor.model.GasData;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class EspClient {

    public interface Listener {
        void onData(GasData data);
        void onError(String msg);
    }

    private final OkHttpClient http;
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Handler pollHandler = new Handler(Looper.getMainLooper());

    private String baseUrl;
    private Listener listener;
    private boolean polling = false;
    private static final int POLL_MS = 1000;

    private final Runnable pollTask = new Runnable() {
        @Override public void run() {
            if (polling) {
                fetch();
                pollHandler.postDelayed(this, POLL_MS);
            }
        }
    };

    public EspClient() {
        http = new OkHttpClient.Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .build();
    }

    public void start(String ip, Listener l) {
        this.baseUrl = "http://" + ip + "/datos";
        this.listener = l;
        this.polling = true;
        pollHandler.post(pollTask);
    }

    public void stop() {
        polling = false;
        pollHandler.removeCallbacks(pollTask);
    }

    private void fetch() {
        Request req = new Request.Builder().url(baseUrl).build();
        http.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> {
                    if (listener != null) listener.onError(e.getMessage());
                });
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                try {
                    GasData data = gson.fromJson(body, GasData.class);
                    mainHandler.post(() -> {
                        if (listener != null) listener.onData(data);
                    });
                } catch (Exception e) {
                    mainHandler.post(() -> {
                        if (listener != null) listener.onError("JSON inválido");
                    });
                }
            }
        });
    }
}
