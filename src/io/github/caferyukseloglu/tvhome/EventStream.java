package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

/**
 * The bridge's live event stream (Server-Sent Events): any change, from the Hue app, a wall switch
 * or this TV, arrives within a moment, so the lights row no longer has to ask every few seconds.
 * One long-lived HTTPS request on its own thread; it reconnects after a drop.
 */
final class EventStream {
    private static final String TAG = "EventStream";
    private static final long RETRY_MS = 5000;
    private static final long SETTLE_MS = 300; // a scene changes several lamps: one refresh for the burst

    private final Context context;
    private final Runnable onChange;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private volatile HttpURLConnection open;

    EventStream(Context context, Runnable onChange) {
        this.context = context;
        this.onChange = onChange;
    }

    void start() {
        if (running) return;
        running = true;
        new Thread(this::loop, TAG).start();
    }

    void stop() {
        running = false;
        HttpURLConnection c = open;
        if (c != null) c.disconnect(); // unblocks the reading thread
        main.removeCallbacks(onChange);
    }

    private void loop() {
        while (running) {
            try {
                open = new HueV2(context).events();
                try (BufferedReader in = new BufferedReader(new InputStreamReader(open.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String line; running && (line = in.readLine()) != null; ) {
                        if (line.startsWith("data:")) {
                            main.removeCallbacks(onChange);
                            main.postDelayed(onChange, SETTLE_MS);
                        }
                    }
                }
            } catch (Exception e) {
                if (running) Log.w(TAG, "stream dropped, retrying", e);
            }
            if (running) sleep();
        }
    }

    private static void sleep() {
        try {
            Thread.sleep(RETRY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
