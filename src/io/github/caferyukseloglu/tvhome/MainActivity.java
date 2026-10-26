package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

/**
 * The home screen, tvOS-style: a quiet header, the Hue lights as the top shelf, the apps as a grid.
 * No section titles, no rows of ads. The page scrolls with focus.
 */
public final class MainActivity extends Activity {
    private static final int EDGE = 60; // dp from the screen edge to text and the first card
    private LightsRow lights;
    private AppGrid grid;
    private FilmMode film;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        film = FilmMode.get(this);
        grid = new AppGrid(this, new Apps(this), film);
        LinearLayout shelf = new LinearLayout(this);
        shelf.setClipChildren(false);
        shelf.setClipToPadding(false); // the padding is where the focus zoom grows into
        shelf.setPadding(Ui.dp(this, EDGE), Ui.dp(this, 18), Ui.dp(this, EDGE), Ui.dp(this, 18));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(0, Ui.dp(this, 28), 0, Ui.dp(this, 40));
        page.addView(header(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroller(shelf));
        View apps = grid.view();
        apps.setPadding(Ui.dp(this, EDGE), Ui.dp(this, 14), Ui.dp(this, EDGE), 0);
        page.addView(apps);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipChildren(false);
        scroll.setFillViewport(true);
        scroll.addView(page);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(backdrop());
        root.addView(scroll);
        setContentView(root);
        lights = new LightsRow(this, shelf, film, 168);
    }

    /** A deep graphite room with a soft light from above, like a lit wall behind the TV. */
    private GradientDrawable backdrop() {
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0f);
        glow.setGradientRadius(Ui.dp(this, 720));
        glow.setColors(new int[] {0xFF23262E, 0xFF121317, 0xFF0A0B0D});
        return glow;
    }

    @Override
    protected void onResume() {
        super.onResume();
        film.end(); // back on Home: the TV lights return to how they were
        grid.show(null);
        lights.resume();
    }

    @Override
    protected void onPause() {
        lights.pause();
        super.onPause();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        return grid.onKey(event) || super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Home is the bottom of the stack: Back has nowhere to go.
    }

    /** Date on the left, time and Settings on the right: small and quiet, the content leads. */
    private View header() {
        TextClock date = clock("EEEE, d MMMM", 17, Ui.TEXT_DIM);
        TextClock time = clock(null, 22, Ui.TEXT);

        TextView settings = Ui.text(this, 15, Ui.TEXT, false);
        settings.setText(R.string.settings);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(Ui.dp(this, 18), Ui.dp(this, 8), Ui.dp(this, 18), Ui.dp(this, 8));
        Ui.focusable(settings, Ui.rounded(this, 0x22FFFFFF), null);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClipChildren(false);
        header.setClipToPadding(false); // the focused Settings pill grows into the margin
        header.setPadding(Ui.dp(this, EDGE), 0, Ui.dp(this, EDGE), 0);
        header.addView(date, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(time);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Ui.dp(this, 20);
        header.addView(settings, p);
        return header;
    }

    /** format null: the system's own 12/24 h time. */
    private TextClock clock(String format, float sp, int color) {
        TextClock c = new TextClock(this);
        if (format != null) {
            c.setFormat24Hour(format);
            c.setFormat12Hour(format);
        } else {
            c.setFormat24Hour("HH:mm");
            c.setFormat12Hour("h:mm");
        }
        c.setTextSize(sp);
        c.setTextColor(color);
        return c;
    }

    /** The shelf scrolls sideways; the fade at the edges doubles as the scroll margin. */
    private HorizontalScrollView scroller(LinearLayout row) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setHorizontalFadingEdgeEnabled(true);
        s.setFadingEdgeLength(Ui.dp(this, EDGE));
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.addView(row);
        return s;
    }
}
