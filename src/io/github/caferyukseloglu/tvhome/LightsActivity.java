package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The control panel, sliding in from the right over whatever is playing (Home's Lights pill, or one
 * remote key from anywhere): tiles for network, Bluetooth, sound, inputs, memory and running apps,
 * the sleep timer, screensaver, screen off, search and settings, then the Hue lights. Each tile
 * shows its live state; tiles whose screen this TV does not have are left out. Back closes it.
 */
public final class LightsActivity extends Activity {
    private static final int PANEL_W = 520; // dp
    private LightsRow lights;
    private final List<Runnable> states = new ArrayList<>(); // re-read each tile's state on resume

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        int pad = Ui.dp(this, 32);
        list.setPadding(pad, Ui.dp(this, 34), pad, pad);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(this, 22, Ui.TEXT, 600);
        title.setText(R.string.control_panel);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextClock clock = new TextClock(this);
        clock.setFormat24Hour("HH:mm");
        clock.setFormat12Hour("h:mm");
        clock.setTextSize(22);
        clock.setTextColor(Ui.TEXT_DIM);
        clock.setTypeface(Ui.font(this, 300));
        top.addView(clock);
        top.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), Ui.dp(this, 18));
        list.addView(top);

        tiles(list);

        TextView lightsTitle = Ui.label(this, 12, Ui.TEXT_DIM);
        lightsTitle.setText(R.string.lights);
        lightsTitle.setPadding(Ui.dp(this, 4), Ui.dp(this, 26), 0, Ui.dp(this, 14));
        list.addView(lightsTitle);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setClipChildren(false);
        list.addView(cards);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false);
        scroll.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[] {0xF7151619, 0xFF0E0F12}));
        scroll.addView(list);

        FrameLayout root = new FrameLayout(this);
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.dp(this, PANEL_W), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        setContentView(root);
        lights = new LightsRow(this, cards, FilmMode.get(this), PANEL_W - 64);
    }

    /** Two tiles a row. */
    private void tiles(LinearLayout list) {
        List<View> all = new ArrayList<>();
        Intent network = new Intent(Settings.ACTION_WIFI_SETTINGS);
        if (opens(network)) all.add(tile(R.string.network, this::networkState, v -> open(network)));
        Intent bluetooth = new Intent("com.google.android.intent.action.CONNECT_INPUT");
        if (opens(bluetooth)) all.add(tile(R.string.bluetooth, this::bluetoothState, v -> open(bluetooth)));
        Intent sound = new Intent(Settings.ACTION_SOUND_SETTINGS);
        if (opens(sound)) all.add(tile(R.string.sound, this::volumeState, v -> open(sound)));
        Intent inputs = new Intent("com.android.tv.action.VIEW_INPUTS");
        if (opens(inputs)) all.add(tile(R.string.inputs, () -> getString(R.string.inputs_detail), v -> open(inputs)));
        all.add(tile(R.string.memory, this::memoryState, v -> new Recents(this).show()));
        // Sleep timer and screen off need the key service (./home.sh panel-key); without it they are left out.
        if (PanelKey.on()) all.add(tile(R.string.sleep_timer, this::sleepState, v -> {
            PanelKey.nextSleep();
            refresh();
        }));
        Intent saver = new Intent().setComponent(new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"));
        if (opens(saver)) all.add(tile(R.string.screensaver_now, () -> getString(R.string.screensaver_detail), v -> open(saver)));
        if (PanelKey.on()) all.add(tile(R.string.screen_off, () -> getString(R.string.screen_off_detail), v -> PanelKey.screenOff()));
        Intent search = new Intent(Intent.ACTION_ASSIST);
        if (opens(search)) all.add(tile(R.string.search, () -> getString(R.string.search_detail), v -> open(search)));
        all.add(tile(R.string.settings, () -> getString(R.string.tv_settings), v -> open(new Intent(Settings.ACTION_SETTINGS))));

        for (int i = 0; i < all.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setClipChildren(false);
            for (int j = i; j < Math.min(i + 2, all.size()); j++) {
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, Ui.dp(this, 74), 1);
                if (j == i) p.rightMargin = Ui.dp(this, 12);
                row.addView(all.get(j), p);
            }
            if (all.size() - i == 1) row.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1)); // odd one keeps its width
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = Ui.dp(this, 12);
            list.addView(row, rp);
        }
    }

    private interface State {
        String now();
    }

    /** A tile: its name over its state; white when focused, like every menu row. */
    private View tile(int name, State state, View.OnClickListener action) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 14), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(this, 22));
        bg.setColor(0x1FFFFFFF);
        t.setBackground(bg);
        TextView label = Ui.text(this, 16, Ui.TEXT, 600);
        label.setText(name);
        TextView detail = Ui.text(this, 13, Ui.TEXT_DIM, 400);
        t.addView(label);
        t.addView(detail);
        t.setFocusable(true);
        t.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0x1FFFFFFF);
            label.setTextColor(has ? 0xFF111216 : Ui.TEXT);
            detail.setTextColor(has ? 0xFF4B4E57 : Ui.TEXT_DIM);
        });
        t.setOnClickListener(action);
        states.add(() -> detail.setText(state.now()));
        return t;
    }

    private void refresh() {
        for (Runnable r : states) r.run();
    }

    private boolean opens(Intent i) {
        return i.resolveActivity(getPackageManager()) != null;
    }

    private void open(Intent i) {
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private String networkState() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (caps == null) return getString(R.string.offline);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.ethernet);
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return getString(R.string.wifi);
        return getString(R.string.connected);
    }

    private String bluetoothState() {
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        return getString(bt != null && bt.isEnabled() ? R.string.on : R.string.off);
    }

    private String volumeState() {
        AudioManager audio = getSystemService(AudioManager.class);
        int max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        return getString(R.string.volume_percent, max == 0 ? 0 : audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max);
    }

    private String memoryState() {
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(m);
        return getString(R.string.memory_used, (int) ((m.totalMem - m.availMem) * 100 / m.totalMem));
    }

    private String sleepState() {
        int left = PanelKey.sleepLeft();
        return left == 0 ? getString(R.string.off) : getString(R.string.minutes_left, left);
    }

    @Override
    public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        Ui.noteKey(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        lights.resume();
    }

    @Override
    protected void onPause() {
        lights.pause();
        super.onPause();
        finish(); // a panel, not a place: leaving it closes it
    }
}
