package io.github.caferyukseloglu.tvhome;

import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Hold OK on a light: brightness (Left/Right), a row of colors, and screen sync for the TV strip. */
final class LightPanel extends Dialog {
    interface Listener {
        void changed();

        /** lightId of the strip to start screen sync on, or null to stop it. */
        void ambilight(String lightId);
    }

    /** 0xRRGGBB shown on the swatch; a white swatch sends its color temperature (mired) instead of xy. */
    private static final int[][] SWATCHES = {
        {0xFFB46B, 454}, {0xFFE4C4, 300}, {0xEEF3FF, 200},
        {0xFF3B30, 0}, {0xFF9500, 0}, {0xFFD60A, 0}, {0x34C759, 0}, {0x30B0C7, 0}, {0x0A84FF, 0}, {0xBF5AF2, 0}, {0xFF2D92, 0},
    };
    private static final int STEP = 25; // brightness step on the 1-254 scale, about 10%

    private final Context context;
    private final Hue hue;
    private final Hue.Light light;
    private final Listener listener;
    private final TextView briLabel;
    private final View briFill;
    private final Runnable sendBrightness = () -> send(json("on", true, "bri", this.bri));
    private int bri;

    LightPanel(Context context, Hue hue, Hue.Light light, Listener listener) {
        super(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        this.context = context;
        this.hue = hue;
        this.light = light;
        this.listener = listener;
        this.bri = light.bri;
        briLabel = Ui.text(context, 15, Ui.TEXT, false);
        briFill = new View(context);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        int pad = Ui.dp(context, 28);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(Ui.rounded(context, 0xFF17191E));
        TextView title = Ui.text(context, 22, Ui.TEXT, true);
        title.setText(light.name);
        panel.addView(title);
        panel.addView(brightnessBar(), spaced(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 48)));
        panel.addView(swatches(), spaced(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (light.gradient) panel.addView(ambilightButton(), spaced(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(panel, new ViewGroup.LayoutParams(Ui.dp(context, 620), ViewGroup.LayoutParams.WRAP_CONTENT));
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        showBrightness();
    }

    private LinearLayout.LayoutParams spaced(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = Ui.dp(context, 20);
        return p;
    }

    private View brightnessBar() {
        FrameLayout bar = new FrameLayout(context);
        bar.addView(briFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        briLabel.setPadding(Ui.dp(context, 16), 0, 0, 0);
        bar.addView(briLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_VERTICAL));
        briLabel.setGravity(Gravity.CENTER_VERTICAL);
        briFill.setBackground(Ui.rounded(context, 0xFF3A3D46));
        Ui.focusable(bar, Ui.rounded(context, Ui.CARD), null);
        bar.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
            int next = Math.max(1, Math.min(254, bri + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? STEP : -STEP)));
            if (next == bri) return true;
            bri = next;
            showBrightness();
            // A held key repeats ~20 times a second; the bridge takes ~10, so only the last value is sent.
            bar.removeCallbacks(sendBrightness);
            bar.postDelayed(sendBrightness, 150);
            return true;
        });
        return bar;
    }

    private void showBrightness() {
        briLabel.setText(context.getString(R.string.brightness, Math.round(bri * 100f / 254)));
        View bar = (View) briFill.getParent();
        bar.post(() -> {
            ViewGroup.LayoutParams p = briFill.getLayoutParams();
            p.width = Math.round(bar.getWidth() * bri / 254f);
            briFill.setLayoutParams(p);
        });
    }

    private View swatches() {
        LinearLayout row = new LinearLayout(context);
        row.setClipChildren(false);
        int size = Ui.dp(context, 40);
        for (int[] s : SWATCHES) {
            boolean white = s[1] != 0;
            if (!white && !light.color) continue;
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(0xFF000000 | s[0]);
            View v = new View(context);
            Ui.focusable(v, dot, null);
            v.setOnClickListener(x -> {
                send(white ? json("on", true, "ct", s[1])
                        : json("on", true, "xy", Colors.rgbToXy((s[0] >> 16) & 255, (s[0] >> 8) & 255, s[0] & 255)));
            });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.rightMargin = Ui.dp(context, 12);
            row.addView(v, p);
        }
        return row;
    }

    private View ambilightButton() {
        TextView b = Ui.text(context, 15, Ui.TEXT, false);
        b.setText(Ambilight.running ? R.string.ambilight_stop : R.string.ambilight_start);
        b.setPadding(Ui.dp(context, 20), Ui.dp(context, 12), Ui.dp(context, 20), Ui.dp(context, 12));
        Ui.focusable(b, Ui.rounded(context, Ui.CARD), null);
        b.setOnClickListener(v -> {
            listener.ambilight(Ambilight.running ? null : light.id);
            dismiss();
        });
        return b;
    }

    private void send(JSONObject state) {
        hue.setState(light.id, state, (ok, error) -> listener.changed());
    }

    /** json("on", true, "xy", new double[] {x, y}) -> {"on":true,"xy":[x,y]} */
    static JSONObject json(Object... keysAndValues) {
        try {
            JSONObject o = new JSONObject();
            for (int i = 0; i < keysAndValues.length; i += 2) {
                Object v = keysAndValues[i + 1];
                if (v instanceof double[]) {
                    JSONArray a = new JSONArray();
                    for (double d : (double[]) v) a.put(d);
                    v = a;
                }
                o.put((String) keysAndValues[i], v);
            }
            return o;
        } catch (JSONException e) {
            throw new IllegalArgumentException(e); // only NaN or a null key gets here
        }
    }
}
