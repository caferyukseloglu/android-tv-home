package io.github.caferyukseloglu.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import java.util.Calendar;

/**
 * The home screen, tvOS-style: a greeting and a thin clock, the focused app's name large over a
 * glow in its color, a dock of the first five apps and the rest as a grid. The page glides with
 * focus and the header fades as the grid comes up. The look follows the theme (Halloween in its week).
 */
public final class MainActivity extends Activity implements AppGrid.Host {
    private static final int EDGE = 60; // dp from the screen edge to text and the first card
    private static final int GLOW_ALPHA = 0x5C; // the app color's strength at the glow's center
    private AppGrid grid;
    private FilmMode film;
    private NowPlaying nowPlaying;
    private Glide scroll;
    private TextView weather;
    private TextView greeting;
    private TextView hero;
    private TextView hint;
    private Bats bats;
    private Pumpkin pumpkin;
    private Theme theme;
    private boolean glowOn;
    private String sceneShown; // the scene drawn as the backdrop now, null for a plain room
    private final GradientDrawable room = new GradientDrawable();
    private final GradientDrawable glow = new GradientDrawable();
    private ValueAnimator glowFade;
    private int glowColor; // ARGB at the center, 0 = no glow

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        film = FilmMode.get(this);
        grid = new AppGrid(this, new Apps(this), this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(0, Ui.dp(this, 30), 0, Ui.dp(this, 60));
        View header = header();
        page.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout playing = new LinearLayout(this);
        playing.setClipChildren(false);
        playing.setClipToPadding(false);
        playing.setPadding(Ui.dp(this, EDGE), Ui.dp(this, 16), Ui.dp(this, EDGE), 0);
        page.addView(playing);

        hero = Ui.text(this, 40, Ui.TEXT, 300);
        hero.setPadding(Ui.dp(this, EDGE - 2), Ui.dp(this, 26), Ui.dp(this, EDGE), Ui.dp(this, 12));
        page.addView(hero);

        View apps = grid.view();
        apps.setPadding(Ui.dp(this, EDGE), Ui.dp(this, 14), Ui.dp(this, EDGE), 0);
        page.addView(apps);

        scroll = new Glide(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false); // focus belongs to the cards and pills, never to the scroller
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);
        scroll.addView(page);
        // Above the grid everything fits on one screen: focus there glides the page back to the top.
        scroll.getViewTreeObserver().addOnGlobalFocusChangeListener((was, now) -> {
            if (now != null && !grid.isMoving() && !inside(now, apps)) scroll.glideTo(0);
        });
        // The grid slides up over the header: the clock, the playing card and the title fade out on the way.
        scroll.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            float shown = 1f - Math.min(1f, y / (float) Ui.dp(this, 180));
            header.setAlpha(shown);
            playing.setAlpha(shown);
            hero.setAlpha(Math.max(0.25f, shown));
        });

        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.28f, 0.62f);
        glow.setGradientRadius(Ui.dp(this, 620));
        glow.setColors(new int[] {0, 0});
        View ambient = new View(this);
        ambient.setBackground(glow);
        room.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        room.setGradientCenter(0.5f, 0f);
        room.setGradientRadius(Ui.dp(this, 720));

        bats = new Bats(this);
        hint = Ui.text(this, 14, Ui.TEXT, 500);
        hint.setText(R.string.move_hint);
        hint.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xD9202228);
        pill.setStroke(Ui.dp(this, 1), 0x26FFFFFF);
        pill.setCornerRadius(Ui.dp(this, 24));
        hint.setBackground(pill);
        hint.setAlpha(0f);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hp.bottomMargin = Ui.dp(this, 28);

        FrameLayout root = new FrameLayout(this);
        root.addView(ambient);
        root.addView(scroll);
        root.addView(bats);
        root.addView(hint, hp);
        setContentView(root);
        getWindow().setBackgroundDrawable(null); // the room covers it: one full-screen fill less per frame
        nowPlaying = new NowPlaying(this, playing);
    }

    private static boolean inside(View v, View parent) {
        for (android.view.ViewParent p = v.getParent(); p instanceof View; p = p.getParent()) {
            if (p == parent) return true;
        }
        return false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        FilmMode.homeVisible = true;
        film.end(); // back on Home: the TV lights return to how they were
        applySettings();
        scroll.scrollTo(0, 0); // Home always opens at the top
        grid.show(null);
        nowPlaying.resume();
        new WeatherClient(this).load(w -> weather.setText(w == null ? "" : WeatherClient.describe(this, w)));
    }

    @Override
    protected void onPause() {
        FilmMode.homeVisible = false;
        nowPlaying.pause();
        if (pumpkin != null) pumpkin.stop(); // no candle flicker while an app is on screen; onResume lights a new one
        super.onPause();
    }

    /** Theme and switches from Settings, read every time Home comes back. */
    private void applySettings() {
        SharedPreferences prefs = Theme.prefs(this);
        theme = Theme.current(this);
        Ui.reduced = prefs.getBoolean("reduced_motion", false);
        glowOn = theme.glow && prefs.getBoolean("glow", true);
        room.setColors(theme.room);
        Scene scene = Scene.of(theme.id);
        View root = findViewById(android.R.id.content);
        if (scene == null) {
            root.setBackground(room);
        } else if (!theme.id.equals(sceneShown)) { // drawn once per change, half size: it is soft by nature
            android.util.DisplayMetrics m = getResources().getDisplayMetrics();
            root.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), scene.render(m.widthPixels / 2, m.heightPixels / 2)));
        }
        sceneShown = scene == null ? null : theme.id;
        greeting.setVisibility(prefs.getBoolean("greeting", true) ? View.VISIBLE : View.GONE);
        if (pumpkin != null) pumpkin.stop();
        pumpkin = null;
        if (theme.festive) {
            greeting.setText(R.string.greeting_halloween);
            greeting.setTextColor(0xFFFFB066);
            pumpkin = new Pumpkin(Ui.dp(this, 26), Ui.reduced);
            greeting.setCompoundDrawablesRelativeWithIntrinsicBounds(pumpkin, null, null, null);
            greeting.setCompoundDrawablePadding(Ui.dp(this, 10));
            String today = String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_YEAR));
            if (!Ui.reduced && !today.equals(prefs.getString("bats_day", ""))) { // the colony flies once a day
                prefs.edit().putString("bats_day", today).apply();
                bats.postDelayed(bats::fly, 500);
            }
        } else {
            greeting.setText(greeting());
            greeting.setTextColor(Ui.TEXT_DIM);
            greeting.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null);
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        Ui.noteKey(event);
        return grid.onKey(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Home is the bottom of the stack: Back has nowhere to go.
    }

    /** A card took focus: its name becomes the title and the room takes on its color. */
    @Override
    public void focused(Apps.App app, int accent, boolean dock) {
        if (dock && !grid.isMoving()) scroll.glideTo(0); // the dock lives under the header: show it whole
        hero.setText(app.hidden ? getString(R.string.hidden) + " · " + app.label : app.label);
        int rgb = accent == -1 ? theme.fallback : accent;
        int target = !glowOn || rgb == 0 ? 0 : GLOW_ALPHA << 24 | rgb;
        if (glowFade != null) glowFade.cancel();
        glowFade = ValueAnimator.ofObject(new ArgbEvaluator(), glowColor, target);
        glowFade.setDuration(Ui.reduced ? 120 : 520);
        glowFade.setInterpolator(Ui.SETTLE);
        glowFade.addUpdateListener(a -> {
            glowColor = (int) a.getAnimatedValue();
            glow.setColors(new int[] {glowColor, glowColor & 0x00FFFFFF});
        });
        glowFade.start();
    }

    @Override
    public void moving(boolean on) {
        hint.animate().alpha(on ? 1f : 0f).translationY(on ? 0 : Ui.dp(this, 12)).setDuration(200).start();
    }

    @Override
    public void keepScroll(Runnable relayout) {
        int y = scroll.getScrollY();
        relayout.run();
        scroll.scrollTo(0, y);
        scroll.post(() -> scroll.scrollTo(0, y)); // the new rows lay out on the next frame
    }

    private int greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour >= 5 && hour < 12) return R.string.greeting_morning;
        if (hour >= 12 && hour < 18) return R.string.greeting_afternoon;
        if (hour >= 18 && hour < 23) return R.string.greeting_evening;
        return R.string.greeting_night;
    }

    /** Greeting over a thin clock, the date and weather beside it; Lights and Settings on the right. */
    private View header() {
        greeting = Ui.label(this, 13, Ui.TEXT_DIM);

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextSize(64);
        time.setTextColor(Ui.TEXT);
        time.setTypeface(Ui.font(this, 200));
        time.setIncludeFontPadding(false);

        View divider = new View(this);
        divider.setBackgroundColor(0x33FFFFFF);
        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE  d MMMM");
        date.setFormat12Hour("EEEE  d MMMM");
        date.setTextSize(14);
        date.setTextColor(Ui.TEXT_DIM);
        date.setTypeface(Ui.font(this, 500));
        date.setAllCaps(true);
        date.setLetterSpacing(0.14f);
        weather = Ui.label(this, 14, Ui.TEXT_DIM);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.addView(time);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 1), Ui.dp(this, 30));
        dp.leftMargin = dp.rightMargin = Ui.dp(this, 22);
        clockRow.addView(divider, dp);
        clockRow.addView(date);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wp.leftMargin = Ui.dp(this, 22);
        clockRow.addView(weather, wp);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(greeting);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.topMargin = Ui.dp(this, 6);
        left.addView(clockRow, cp);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false); // the focused pills grow into the margin
        header.setPadding(Ui.dp(this, EDGE), 0, Ui.dp(this, EDGE), 0);
        header.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(pill(R.string.controls, v -> startActivity(new Intent(this, LightsActivity.class))));
        View settings = pill(R.string.settings, this::settingsMenu);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.leftMargin = Ui.dp(this, 12);
        header.addView(settings, sp);
        return header;
    }

    /** Settings opens a small glass menu: the TV's settings, this home screen's, and running apps. */
    private void settingsMenu(View anchor) {
        Sheet menu = new Sheet(this, null);
        menu.add(getString(R.string.tv_settings), () -> {
            menu.dismiss();
            startActivity(new Intent(Settings.ACTION_SETTINGS));
        });
        menu.add(getString(R.string.home_settings), () -> {
            menu.dismiss();
            startActivity(new Intent(this, SettingsActivity.class));
        });
        menu.add(getString(R.string.running_apps), () -> {
            menu.dismiss();
            new Recents(this).show();
        });
        menu.showAt(anchor, 300);
    }

    /** A frosted pill; focus adds the white ring (Ui.focusable owns the stroke, so none here). */
    private View pill(int text, View.OnClickListener action) {
        TextView pill = Ui.text(this, 15, Ui.TEXT, 500);
        pill.setText(text);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x24FFFFFF);
        bg.setCornerRadius(Ui.dp(this, 24));
        Ui.focusable(pill, bg, null);
        pill.setRevealOnFocusHint(false);
        pill.setOnClickListener(action);
        return pill;
    }

    /**
     * The page scroller, but gliding: focus moves ease the page into place instead of jumping
     * (ScrollView's own jump is off for Home's cards and pills, see setRevealOnFocusHint).
     */
    private static final class Glide extends ScrollView {
        private ObjectAnimator glide;

        Glide(android.content.Context c) {
            super(c);
        }

        @Override
        public boolean requestChildRectangleOnScreen(View child, Rect rect, boolean immediate) {
            rect.offset(child.getLeft() - child.getScrollX(), child.getTop() - child.getScrollY());
            int delta = computeScrollDeltaToGetChildRectOnScreen(rect);
            if (delta != 0) glideTo(getScrollY() + delta);
            return delta != 0;
        }

        void glideTo(int y) {
            if (glide != null) glide.cancel();
            if (y == getScrollY()) return;
            glide = ObjectAnimator.ofInt(this, "scrollY", y);
            glide.setDuration(Ui.reduced ? 120 : 420);
            glide.setInterpolator(new android.view.animation.DecelerateInterpolator(2f));
            glide.start();
        }
    }
}
