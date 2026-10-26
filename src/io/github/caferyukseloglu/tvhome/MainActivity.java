package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

/** The home screen: clock, Hue lights, apps. Nothing else. */
public final class MainActivity extends Activity {
    private static final int EDGE = 60; // dp from the screen edge to text and the first card
    private Apps apps;
    private LightsRow lights;
    private LinearLayout appRow;
    private TextView appLabel;
    private FilmMode film;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        apps = new Apps(this);
        LinearLayout lightRow = row();
        appRow = row();
        appLabel = Ui.text(this, 17, Ui.TEXT, false);
        appLabel.setPadding(Ui.dp(this, EDGE), 0, 0, 0);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        // Rows span the full width and scroll under the screen edges; text keeps to the EDGE margin.
        content.setPadding(0, Ui.dp(this, 36), 0, Ui.dp(this, 20));
        content.addView(header(), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(sectionTitle(R.string.lights));
        content.addView(scroller(lightRow));
        content.addView(sectionTitle(R.string.apps));
        content.addView(scroller(appRow));
        content.addView(appLabel);

        FrameLayout root = new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xFF17191E, 0xFF0B0C0F}));
        root.addView(content);
        setContentView(root);
        film = new FilmMode(this);
        lights = new LightsRow(this, lightRow, film);
    }

    @Override
    protected void onResume() {
        super.onResume();
        film.end(); // back on Home: the TV light returns to how it was
        showApps(null);
        lights.resume();
    }

    @Override
    protected void onPause() {
        lights.pause();
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        // Home is the bottom of the stack: Back has nowhere to go.
    }

    private void showApps(String focusPkg) {
        appRow.removeAllViews();
        View focus = null;
        for (Apps.App app : apps.load()) {
            View card = appCard(app);
            appRow.addView(card);
            if (app.pkg.equals(focusPkg)) focus = card;
        }
        if (focus == null && appRow.getChildCount() > 0) focus = appRow.getChildAt(0);
        if (focus != null) focus.requestFocus();
    }

    private View appCard(Apps.App app) {
        FrameLayout card = new FrameLayout(this);
        if (app.banner != null) {
            ImageView art = new ImageView(this);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(app.banner);
            card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(app.icon);
            card.addView(icon, new FrameLayout.LayoutParams(Ui.dp(this, 64), Ui.dp(this, 64), Gravity.CENTER));
        }
        card.setAlpha(app.hidden ? 0.35f : 1f);
        card.setContentDescription(app.label);
        String label = app.hidden ? app.label + "  ·  " + getString(R.string.hidden) : app.label.toString();
        Ui.focusable(card, Ui.rounded(this, Ui.CARD), (v, has) -> appLabel.setText(has ? label : ""));
        card.setOnClickListener(v -> {
            film.start(app.pkg);
            apps.launch(app);
        });
        card.setOnLongClickListener(v -> {
            apps.toggleHidden(app.pkg);
            showApps(app.pkg);
            return true;
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(this, 160), Ui.dp(this, 90));
        p.rightMargin = Ui.dp(this, 16);
        card.setLayoutParams(p);
        return card;
    }

    private View header() {
        LinearLayout clock = new LinearLayout(this);
        clock.setOrientation(LinearLayout.VERTICAL);
        clock.addView(clock("HH:mm", "h:mm", 44, Ui.TEXT));
        clock.addView(clock("EEEE, d MMMM", "EEEE, d MMMM", 16, Ui.TEXT_DIM));

        TextView settings = Ui.text(this, 17, Ui.TEXT, false);
        settings.setText(R.string.settings);
        settings.setGravity(Gravity.CENTER);
        settings.setPadding(Ui.dp(this, 22), Ui.dp(this, 10), Ui.dp(this, 22), Ui.dp(this, 10));
        Ui.focusable(settings, Ui.rounded(this, Ui.CARD), null);
        settings.setTextSize(15);
        settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_SETTINGS)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.TOP);
        header.setClipChildren(false);
        header.setPadding(Ui.dp(this, EDGE), 0, Ui.dp(this, EDGE), 0);
        header.addView(clock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        header.addView(settings);
        return header;
    }

    private TextClock clock(String format24, String format12, float sp, int color) {
        TextClock c = new TextClock(this);
        c.setFormat24Hour(format24);
        c.setFormat12Hour(format12);
        c.setTextSize(sp);
        c.setTextColor(color);
        return c;
    }

    private TextView sectionTitle(int res) {
        TextView t = Ui.text(this, 17, Ui.TEXT_DIM, false);
        t.setText(res);
        t.setPadding(Ui.dp(this, EDGE), Ui.dp(this, 14), 0, 0);
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setClipChildren(false);
        r.setClipToPadding(false); // the padding is where the focus zoom grows into
        int v = Ui.dp(this, 20); // room for the focus zoom and its shadow, so nothing is cut
        r.setPadding(Ui.dp(this, EDGE), v, Ui.dp(this, EDGE), v);
        return r;
    }

    /** Focus moves across the row; the row only scrolls to keep the focused card an EDGE away from the screen side. */
    private HorizontalScrollView scroller(LinearLayout row) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setHorizontalFadingEdgeEnabled(true); // the fade length doubles as the scroll margin
        s.setFadingEdgeLength(Ui.dp(this, EDGE));
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.addView(row);
        return s;
    }
}
