package io.github.caferyukseloglu.tvhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.nio.ByteBuffer;

/**
 * Colors the TV's gradient strip from the edges of the picture. The screen is mirrored into a
 * 64x36 image (the GPU does the downscaling), so the work per frame is tiny.
 */
public final class Ambilight extends Service {
    static final String EXTRA_CODE = "code";
    static final String EXTRA_DATA = "data";
    static final String EXTRA_LIGHT = "light"; // Hue v1 light id of the strip to drive
    static volatile boolean running;

    private static final String TAG = "Ambilight";
    private static final int W = 64;
    private static final int H = 36;
    private static final int FRAME_MS = 100; // the bridge takes ~10 updates a second per light
    private static final int MIN_CHANGE = 24; // summed RGB difference below which a frame is not sent

    private HandlerThread thread;
    private Handler worker;
    private MediaProjection projection;
    private VirtualDisplay display;
    private ImageReader reader;
    private HueV2 hue;
    private int[] last;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (running || intent == null) return START_NOT_STICKY;
        startForeground(1, notification());
        running = true;
        thread = new HandlerThread(TAG);
        thread.start();
        worker = new Handler(thread.getLooper());
        MediaProjectionManager mpm = getSystemService(MediaProjectionManager.class);
        projection = mpm.getMediaProjection(intent.getIntExtra(EXTRA_CODE, 0), intent.getParcelableExtra(EXTRA_DATA));
        if (projection == null) {
            fail("no screen capture permission");
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override public void onStop() {
                stopSelf(); // the TV went to standby or another app took the capture
            }
        }, worker);
        String light = intent.getStringExtra(EXTRA_LIGHT);
        worker.post(() -> start(light));
        return START_NOT_STICKY;
    }

    private void start(String light) {
        try {
            hue = new HueV2(this);
            if (!hue.takeOver(light)) {
                fail("light " + light + " is not a gradient strip");
                return;
            }
            if (!running) return; // stopped (standby, or Stop pressed) while the bridge answered
            reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2);
            // Throws if the projection was stopped a moment ago; caught below instead of killing the launcher.
            display = projection.createVirtualDisplay(TAG, W, H, 160, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    reader.getSurface(), null, worker);
        } catch (Exception e) {
            fail(e.toString());
            return;
        }
        worker.post(this::frame);
    }

    /** One frame: read, sample the edges, send if it changed. The send blocks, which paces the loop. */
    private void frame() {
        if (!running) return;
        int[] rgb = read();
        if (rgb != null && !Colors.allBlack(rgb)) {
            int[] colors = Colors.edgeColors(rgb, W, H);
            if (last == null || Colors.distance(colors, last) >= MIN_CHANGE) {
                try {
                    hue.show(colors, Math.max(5, Colors.brightness(colors) * 100), FRAME_MS);
                    last = colors;
                } catch (Exception e) {
                    Log.w(TAG, "frame not sent", e); // one dropped frame is fine; the next one retries
                }
            }
        }
        worker.postDelayed(this::frame, FRAME_MS);
    }

    private int[] read() {
        Image image = reader.acquireLatestImage();
        if (image == null) return null;
        try {
            Image.Plane plane = image.getPlanes()[0];
            ByteBuffer buf = plane.getBuffer();
            int[] rgb = new int[W * H];
            for (int y = 0; y < H; y++) {
                for (int x = 0; x < W; x++) {
                    int i = y * plane.getRowStride() + x * plane.getPixelStride();
                    rgb[y * W + x] = (buf.get(i) & 255) << 16 | (buf.get(i + 1) & 255) << 8 | (buf.get(i + 2) & 255);
                }
            }
            return rgb;
        } finally {
            image.close();
        }
    }

    private void fail(String why) {
        Log.e(TAG, why);
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(this, R.string.ambilight_failed, Toast.LENGTH_LONG).show());
        stopSelf();
    }

    @Override
    public void onDestroy() {
        running = false;
        if (projection != null) projection.stop();
        if (worker != null) {
            worker.removeCallbacksAndMessages(null);
            worker.post(() -> {
                try {
                    if (hue != null) hue.restore();
                } catch (Exception e) {
                    Log.w(TAG, "strip not restored", e);
                }
                // Released here, after start() is done on this thread, so a display it made late is freed too.
                if (display != null) display.release();
                if (reader != null) reader.close();
                thread.quitSafely();
            });
        }
        super.onDestroy();
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(TAG, getString(R.string.ambilight), NotificationManager.IMPORTANCE_MIN));
        return new Notification.Builder(this, TAG)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(getString(R.string.ambilight))
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
