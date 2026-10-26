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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Hold OK on a light: film mode (TV strip only), brightness (Left/Right), colors and the
 * bridge's built-in effects, in that order.
 */
final class LightPanel extends Dialog {
    interface Listener {
        void changed();
    }

    /** 0xRRGGBB shown on the swatch; a white swatch sends its color temperature (mired) instead of xy. */
    private static final int[][] SWATCHES = {
        {0xFFB46B, 454}, {0xFFE4C4, 300}, {0xEEF3FF, 200},
        {0xFF3B30, 0}, {0xFF9500, 0}, {0xFFD60A, 0}, {0x34C759, 0}, {0x30B0C7, 0}, {0x0A84FF, 0}, {0xBF5AF2, 0}, {0xFF2D92, 0},
    };
    /** Hue v2 effect names, all supported by current color lamps and gradient strips. */
    private static final Object[][] EFFECTS = {
        {"no_effect", R.string.effect_none}, {"candle", R.string.effect_candle}, {"fire", R.string.effect_fire},
        {"sunbeam", R.string.effect_sunbeam}, {"glisten", R.string.effect_glisten}, {"sparkle", R.string.effect_sparkle},
        {"opal", R.string.effect_opal}, {"prism", R.string.effect_prism}, {"underwater", R.string.effect_underwater},
        {"cosmos", R.string.effect_cosmos}, {"enchant", R.string.effect_enchant},
    };
    private static final int STEP = 25; // brightness step on the 1-254 scale, about 10%

    private final Context context;
    private final Hue hue;
    private final Hue.Light light;
    private final Listener listener;
    private final FilmMode film;
    private final TextView briLabel;
    private final View briFill;
    private final Runnable sendBrightness = () -> send(json("on", true, "bri", this.bri));
    private int bri;

    LightPanel(Context context, Hue hue, Hue.Light light, FilmMode film, Listener listener) {
        super(context, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        this.context = context;
        this.hue = hue;
        this.light = light;
        this.listener = listener;
        this.film = film;
        this.bri = light.bri;
        briLabel = Ui.text(context, 15, Ui.TEXT, false);
        briFill = new View(context);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        int pad = Ui.dp(context, 28);
        panel.setPadding(pad, Ui.dp(context, 24), pad, pad);
        panel.setBackground(Ui.rounded(context, 0xFF17191E));
        TextView title = Ui.text(context, 22, Ui.TEXT, true);
        title.setText(light.name);
        panel.addView(title);
        if (light.gradient) panel.addView(filmButton(), spaced(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 56)));
        panel.addView(heading(R.string.brightness_title));
        panel.addView(brightnessBar(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(context, 44)));
        panel.addView(heading(R.string.color_title));
        panel.addView(swatches());
        if (light.color) {
            panel.addView(heading(R.string.effect_title));
            panel.addView(effects());
        }
        setContentView(panel, new ViewGroup.LayoutParams(Ui.dp(context, 640), ViewGroup.LayoutParams.WRAP_CONTENT));
        getWindow().setBackgroundDrawable(new ColorDrawable(0));
        showBrightness();
    }

    private LinearLayout.LayoutParams spaced(int w, int h) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.topMargin = Ui.dp(context, 18);
        return p;
    }

    private TextView heading(int res) {
        TextView t = Ui.text(context, 14, Ui.TEXT_DIM, false);
        t.setText(res);
        t.setPadding(0, Ui.dp(context, 18), 0, Ui.dp(context, 8));
        return t;
    }

    /** The headline control on the TV strip: bias lighting while video apps are open. */
    private View filmButton() {
        boolean on = light.id.equals(film.light());
        LinearLayout b = new LinearLayout(context);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.dp(context, 20), 0, Ui.dp(context, 20), 0);
        TextView label = Ui.text(context, 17, Ui.TEXT, true);
        label.setText(R.string.film_mode);
        TextView state = Ui.text(context, 15, on ? Ui.TEXT : Ui.TEXT_DIM, false);
        state.setText(on ? R.string.on : R.string.off);
        b.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        b.addView(state);
        Ui.focusable(b, Ui.rounded(context, on ? 0xFF2F5BD3 : Ui.CARD), null, 1f);
        b.setOnClickListener(v -> {
            film.setLight(on ? null : light.id);
            dismiss();
        });
        return b;
    }

    private View brightnessBar() {
        FrameLayout bar = new FrameLayout(context);
        briFill.setBackground(Ui.rounded(context, 0xFF3A3D46));
        bar.addView(briFill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        briLabel.setPadding(Ui.dp(context, 16), 0, 0, 0);
        briLabel.setGravity(Gravity.CENTER_VERTICAL);
        bar.addView(briLabel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Ui.focusable(bar, Ui.rounded(context, Ui.CARD), null, 1f);
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
            v.setOnClickListener(x -> send(white ? json("on", true, "ct", s[1])
                    : json("on", true, "xy", Colors.rgbToXy((s[0] >> 16) & 255, (s[0] >> 8) & 255, s[0] & 255))));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(size, size);
            p.rightMargin = Ui.dp(context, 12);
            row.addView(v, p);
        }
        return row;
    }

    /** Effect chips scroll sideways: eleven of them do not fit the panel's width. */
    private View effects() {
        LinearLayout row = new LinearLayout(context);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        int v = Ui.dp(context, 6);
        row.setPadding(Ui.dp(context, 4), v, Ui.dp(context, 4), v);
        for (Object[] e : EFFECTS) {
            TextView chip = Ui.text(context, 15, Ui.TEXT, false);
            chip.setText((int) e[1]);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(Ui.dp(context, 18), 0, Ui.dp(context, 18), 0);
            Ui.focusable(chip, Ui.rounded(context, Ui.CARD), null);
            String name = (String) e[0];
            chip.setOnClickListener(x -> hue.async(() -> {
                new HueV2(context).effect(light.id, name);
                return null;
            }, (ok, error) -> listener.changed()));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(context, 40));
            p.rightMargin = Ui.dp(context, 10);
            row.addView(chip, p);
        }
        HorizontalScrollView scroller = new HorizontalScrollView(context);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.addView(row);
        return scroller;
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
