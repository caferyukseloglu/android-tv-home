package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Home settings, two panes: sections on the left, their choices on the right. Every choice is saved
 * at once and Home picks it up when it comes back; Back closes.
 */
public final class SettingsActivity extends Activity {
    private static final int[] SECTIONS = {R.string.section_look, R.string.section_home, R.string.section_apps, R.string.section_about};
    private SharedPreferences prefs;
    private final View[] sectionRows = new View[SECTIONS.length];
    private LinearLayout detail;
    private TextView detailTitle;
    private int section;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Theme.prefs(this);
        int edge = Ui.dp(this, 60);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(this, 30, Ui.TEXT, 500);
        title.setText(R.string.home_settings);
        title.setPadding(Ui.dp(this, 14), 0, 0, Ui.dp(this, 28));
        left.addView(title);
        for (int i = 0; i < SECTIONS.length; i++) {
            int index = i;
            View row = Sheet.row(this, left, null, getString(SECTIONS[i]), null, () -> detail.getChildAt(0).requestFocus());
            sectionRows[i] = row;
            row.setOnFocusChangeListener(chain(row.getOnFocusChangeListener(), has -> {
                if (has && section != index) show(index);
                if (!has && section == index) mark(row, true); // focus went right: the open section stays lit
            }));
        }

        detailTitle = Ui.text(this, 22, Ui.TEXT, 500);
        detailTitle.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), 0, Ui.dp(this, 22));
        detail = new LinearLayout(this);
        detail.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false); // or Left/Right can land on the invisible scroller instead of a row
        scroll.addView(detail);
        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.addView(detailTitle);
        right.addView(scroll);

        LinearLayout page = new LinearLayout(this);
        page.setPadding(edge, Ui.dp(this, 44), edge, Ui.dp(this, 30));
        page.addView(left, new LinearLayout.LayoutParams(Ui.dp(this, 300), ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rp.leftMargin = Ui.dp(this, 50);
        page.addView(right, rp);
        GradientDrawable bg = new GradientDrawable();
        bg.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bg.setGradientCenter(0.2f, 0f);
        bg.setGradientRadius(Ui.dp(this, 900));
        bg.setColors(Theme.current(this).room);
        page.setBackground(bg);
        setContentView(page);
        show(0);
        left.getChildAt(1).requestFocus();
    }

    private interface Focus {
        void changed(boolean has);
    }

    private static View.OnFocusChangeListener chain(View.OnFocusChangeListener first, Focus then) {
        return (v, has) -> {
            first.onFocusChange(v, has);
            then.changed(has);
        };
    }

    private static void mark(View row, boolean open) {
        ((GradientDrawable) row.getBackground()).setColor(open ? 0x1FFFFFFF : 0);
    }

    private void show(int index) {
        for (int i = 0; i < sectionRows.length; i++) if (sectionRows[i] != null && !sectionRows[i].hasFocus()) mark(sectionRows[i], i == index);
        section = index;
        detailTitle.setText(SECTIONS[index]);
        detail.removeAllViews();
        switch (index) {
            case 0: look(); break;
            case 1: home(); break;
            case 2: apps(); break;
            default: about(); break;
        }
    }

    /** Re-draw this pane after a change, keeping focus on the row that was pressed. */
    private void refresh(int row) {
        show(section);
        if (row < detail.getChildCount()) detail.getChildAt(row).requestFocus();
    }

    private void look() {
        String chosen = prefs.getString("theme", "auto");
        for (int i = 0; i < Theme.IDS.length; i++) {
            String id = Theme.IDS[i];
            int row = i;
            Sheet.row(this, detail, null, getString(Theme.label(id)), id.equals(chosen) ? "✓" : null, () -> {
                prefs.edit().putString("theme", id).apply();
                refresh(row);
            });
        }
    }

    private void home() {
        toggle(0, R.string.setting_glow, "glow", true);
        toggle(1, R.string.setting_greeting, "greeting", true);
        toggle(2, R.string.setting_motion, "reduced_motion", false);
    }

    private void toggle(int row, int label, String key, boolean byDefault) {
        boolean on = prefs.getBoolean(key, byDefault);
        Sheet.row(this, detail, null, getString(label), getString(on ? R.string.on : R.string.off), () -> {
            prefs.edit().putBoolean(key, !on).apply();
            refresh(row);
        });
    }

    private void apps() {
        Apps apps = new Apps(this);
        ArrayList<Apps.App> hidden = new ArrayList<>();
        for (Apps.App a : apps.load()) if (a.hidden) hidden.add(a);
        Sheet.row(this, detail, null, getString(R.string.reset_order), null, () -> {
            apps.saveOrder(new ArrayList<>());
            refresh(0);
        });
        for (Apps.App a : hidden) {
            Sheet.row(this, detail, a.icon, a.label, getString(R.string.show), () -> {
                apps.toggleHidden(a.pkg);
                refresh(0);
            });
        }
    }

    private void about() {
        String version;
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception unknown) {
            version = "?";
        }
        Sheet.row(this, detail, null, getString(R.string.version), version, () -> {});
        Sheet.row(this, detail, null, "github.com/caferyukseloglu/android-tv-home", null, () -> {});
    }
}
