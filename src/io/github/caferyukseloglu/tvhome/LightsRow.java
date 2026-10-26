package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The Lights row: one card per Hue lamp (OK switches, hold OK for color), or a status card while connecting. */
final class LightsRow {
    private static final long REFRESH_MS = 5000; // picks up switches flipped in the Hue app or on the wall

    private final Context context;
    private final Hue hue;
    private final LinearLayout row;
    private final LightPanel.Listener panelListener = this::refreshSoon;
    private final FilmMode film;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, LightCard> cards = new LinkedHashMap<>();
    private final Runnable refresh = this::refresh;
    private StatusCard status;
    private boolean resumed;

    private final int cardWidth; // dp; the side panel stacks wider cards than the home row

    LightsRow(Context context, LinearLayout row, FilmMode film, int cardWidthDp) {
        this.cardWidth = cardWidthDp;
        this.context = context;
        this.hue = new Hue(context);
        this.row = row;
        this.film = film;
    }

    void resume() {
        resumed = true;
        if (hue.paired()) {
            refresh();
        } else {
            showStatus(R.string.hue_connect, R.string.hue_connect_hint, this::connect);
        }
    }

    void pause() {
        resumed = false;
        main.removeCallbacks(refresh);
    }

    private void refreshSoon() {
        main.removeCallbacks(refresh);
        if (resumed) main.postDelayed(refresh, 600);
    }

    private void refresh() {
        main.removeCallbacks(refresh);
        hue.lights((lights, error) -> {
            if (!resumed) return;
            if (error != null) {
                showStatus(hue.paired() ? R.string.hue_offline : R.string.hue_connect, 0, this::connect);
                if (hue.paired()) main.postDelayed(refresh, REFRESH_MS); // after standby the network needs a moment
                return;
            }
            showLights(lights);
            main.postDelayed(refresh, REFRESH_MS);
        });
    }

    /** Find the bridge (its address can change), pair if needed, then show the lights. */
    private void connect() {
        showStatus(R.string.hue_searching, 0, null);
        hue.discover((ip, error) -> {
            if (error != null) {
                showStatus(R.string.hue_not_found, 0, this::connect);
            } else if (hue.paired()) {
                refresh();
            } else {
                showStatus(R.string.hue_press, 0, null);
                hue.pair((ok, pairError) -> {
                    if (pairError != null) showStatus(R.string.hue_not_pressed, 0, this::connect);
                    else refresh();
                });
            }
        });
    }

    private void showLights(List<Hue.Light> lights) {
        if (lights.isEmpty()) {
            showStatus(R.string.hue_no_lights, 0, null);
            return;
        }
        List<String> ids = new ArrayList<>();
        for (Hue.Light l : lights) ids.add(l.id);
        if (status != null || !ids.equals(new ArrayList<>(cards.keySet()))) {
            boolean hadFocus = row.hasFocus();
            row.removeAllViews();
            cards.clear();
            status = null;
            for (Hue.Light l : lights) {
                LightCard card = new LightCard();
                cards.put(l.id, card);
                row.addView(card.view, cardParams(cardWidth, 80));
            }
            if (hadFocus) row.getChildAt(0).requestFocus();
        }
        for (Hue.Light l : lights) cards.get(l.id).show(l);
    }

    /** Updates the status card in place when it is already there, so focus stays on it. */
    private void showStatus(int title, int hint, Runnable action) {
        if (status == null) {
            boolean hadFocus = row.hasFocus();
            row.removeAllViews();
            cards.clear();
            status = new StatusCard();
            row.addView(status.view, cardParams(Math.max(cardWidth, 300), 80));
            if (hadFocus) status.view.requestFocus();
        }
        status.show(title, hint, action);
    }

    private LinearLayout.LayoutParams cardParams(int widthDp, int heightDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(context, widthDp), Ui.dp(context, heightDp));
        if (row.getOrientation() == LinearLayout.VERTICAL) p.bottomMargin = Ui.dp(context, 14);
        else p.rightMargin = Ui.dp(context, 16);
        return p;
    }

    private LinearLayout cardLayout(GradientDrawable bg) {
        LinearLayout v = new LinearLayout(context);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(context, 16);
        v.setPadding(pad, 0, pad, 0);
        Ui.focusable(v, bg, null);
        return v;
    }

    private final class LightCard {
        final GradientDrawable bg = Ui.rounded(context, Ui.CARD);
        final LinearLayout view = cardLayout(bg);
        final TextView name = Ui.text(context, 16, Ui.TEXT, true);
        final TextView state = Ui.text(context, 13, Ui.TEXT_DIM, false);
        Hue.Light light;
        boolean on;

        LightCard() {
            view.addView(name);
            view.addView(state);
            view.setOnClickListener(v -> {
                boolean next = !on;
                paint(next); // show it at once; the bridge follows within a moment
                main.removeCallbacks(refresh);
                hue.setState(light.id, LightPanel.json("on", next), (ok, error) -> {
                    if (error != null) paint(!next);
                    refreshSoon();
                });
            });
            view.setOnLongClickListener(v -> {
                new LightPanel(context, hue, light, film, panelListener).show();
                return true;
            });
        }

        void show(Hue.Light l) {
            light = l;
            name.setText(l.name);
            paint(l.on);
        }

        /** On: the card takes a tint of the lamp's real color. Off at the wall switch: dimmed text. */
        void paint(boolean isOn) {
            on = isOn;
            boolean lit = isOn && light.reachable;
            bg.setColor(0xFF000000 | (lit ? Colors.mix(Ui.CARD & 0xFFFFFF, light.rgb, 0.45) : Ui.CARD & 0xFFFFFF));
            if (!light.reachable) state.setText(R.string.unreachable);
            else if (isOn) state.setText(context.getString(R.string.on_percent, Math.round(light.bri * 100f / 254)));
            else state.setText(R.string.off);
            state.setTextColor(lit ? Ui.TEXT : Ui.TEXT_DIM);
        }
    }

    private final class StatusCard {
        final LinearLayout view = cardLayout(Ui.rounded(context, Ui.CARD));
        final TextView title = Ui.text(context, 17, Ui.TEXT, true);
        final TextView hint = Ui.text(context, 13, Ui.TEXT_DIM, false);
        Runnable action;

        StatusCard() {
            view.addView(title);
            view.addView(hint);
            view.setOnClickListener(v -> {
                if (action != null) action.run();
            });
        }

        void show(int titleRes, int hintRes, Runnable onClick) {
            title.setText(titleRes);
            hint.setText(hintRes == 0 ? "" : context.getString(hintRes));
            action = onClick;
        }
    }
}
