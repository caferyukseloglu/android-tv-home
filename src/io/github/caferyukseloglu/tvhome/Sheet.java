package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The one glass modal: a panel of rows that drops in under its anchor (or above it when there is no
 * room), or sits in the middle of the screen. The focused row turns white. Back closes it.
 * Rows keep the sheet open; an action that should close it calls {@link #dismiss()}.
 */
final class Sheet {
    private static final int ROW_H = 52;
    private final Activity activity;
    private final Dialog dialog;
    private final FrameLayout root;
    private final LinearLayout panel;
    private final LinearLayout rows;
    private final TextView title;

    Sheet(Activity activity, CharSequence heading) {
        this.activity = activity;
        dialog = new Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        root = new FrameLayout(activity);
        panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(activity, 10);
        panel.setPadding(pad, pad, pad, pad);
        panel.setBackground(glass());
        panel.setElevation(Ui.dp(activity, 24));
        title = Ui.label(activity, 12, Ui.TEXT_DIM);
        title.setPadding(Ui.dp(activity, 14), Ui.dp(activity, 8), Ui.dp(activity, 14), Ui.dp(activity, 8));
        title.setText(heading);
        title.setVisibility(heading == null ? View.GONE : View.VISIBLE);
        panel.addView(title);
        rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int w, int h) { // never taller than 3/4 of the screen
                int max = getResources().getDisplayMetrics().heightPixels * 3 / 4;
                super.onMeasure(w, View.MeasureSpec.makeMeasureSpec(max, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setFocusable(false); // focus belongs to the rows, never to the scroller
        scroll.addView(rows);
        panel.addView(scroll);
        root.addView(panel, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.setOnClickListener(v -> dismiss());
        dialog.setContentView(root);
    }

    void setTitle(CharSequence heading) {
        title.setText(heading);
        title.setVisibility(heading == null ? View.GONE : View.VISIBLE);
    }

    void clear() {
        rows.removeAllViews();
    }

    void focusFirst() {
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }

    /** icon and detail may be null; detail sits on the right, dim: a value, a check, a size. */
    View add(Drawable icon, CharSequence text, CharSequence detail, Runnable action) {
        return row(activity, rows, icon, text, detail, action);
    }

    /** One menu row, added to parent: the same row in every sheet and in Settings. */
    static View row(Activity activity, LinearLayout parent, Drawable icon, CharSequence text, CharSequence detail, Runnable action) {
        LinearLayout row = new LinearLayout(activity);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(activity, 14), 0, Ui.dp(activity, 16), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(activity, 14));
        bg.setColor(0);
        row.setBackground(bg);
        if (icon != null) {
            ImageView i = new ImageView(activity);
            i.setImageDrawable(icon);
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(Ui.dp(activity, 30), Ui.dp(activity, 30));
            ip.rightMargin = Ui.dp(activity, 14);
            row.addView(i, ip);
        }
        TextView label = Ui.text(activity, 16, Ui.TEXT, 500);
        label.setText(text);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView value = Ui.text(activity, 14, Ui.TEXT_DIM, 400);
        if (detail != null) {
            value.setText(detail);
            LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vp.leftMargin = Ui.dp(activity, 18);
            row.addView(value, vp);
        }
        row.setFocusable(true);
        row.setOnFocusChangeListener((v, has) -> {
            bg.setColor(has ? 0xF2FFFFFF : 0);
            label.setTextColor(has ? 0xFF111216 : Ui.TEXT);
            value.setTextColor(has ? 0xFF4B4E57 : Ui.TEXT_DIM);
        });
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, ROW_H));
        rp.bottomMargin = Ui.dp(activity, 4);
        parent.addView(row, rp);
        return row;
    }

    View add(CharSequence text, Runnable action) {
        return add(null, text, null, action);
    }

    /** Drops in under the anchor, left edges aligned; above it when the screen ends first. */
    void showAt(View anchor, int widthDp) {
        int[] at = new int[2];
        anchor.getLocationOnScreen(at);
        show(widthDp, (panelH, screenW, screenH) -> {
            int gap = Ui.dp(activity, 12);
            int x = Math.min(at[0], screenW - Ui.dp(activity, widthDp) - Ui.dp(activity, 40));
            boolean below = at[1] + anchor.getHeight() + gap + panelH <= screenH - gap;
            int y = below ? at[1] + anchor.getHeight() + gap : Math.max(gap, at[1] - gap - panelH);
            return new int[] {x, y, below ? 0 : 1};
        });
    }

    void showCentered(int widthDp) {
        show(widthDp, (panelH, screenW, screenH) ->
                new int[] {(screenW - Ui.dp(activity, widthDp)) / 2, (screenH - panelH) / 2, 2});
    }

    private interface Place {
        int[] at(int panelH, int screenW, int screenH); // x, y, and 0 = drop down, 1 = rise up, 2 = grow
    }

    private void show(int widthDp, Place place) {
        FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) panel.getLayoutParams();
        p.width = Ui.dp(activity, widthDp);
        panel.setAlpha(0f);
        dialog.show();
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
        root.post(() -> {
            int[] xy = place.at(panel.getHeight(), root.getWidth(), root.getHeight());
            p.gravity = Gravity.TOP | Gravity.START;
            p.leftMargin = xy[0];
            p.topMargin = xy[1];
            panel.setLayoutParams(p);
            panel.setPivotX(panel.getWidth() / 2f);
            panel.setPivotY(xy[2] == 0 ? 0 : xy[2] == 1 ? panel.getHeight() : panel.getHeight() / 2f);
            panel.setScaleY(xy[2] == 2 ? 0.96f : 0.92f);
            panel.setScaleX(xy[2] == 2 ? 0.96f : 1f);
            panel.setTranslationY(xy[2] == 0 ? -Ui.dp(activity, 8) : xy[2] == 1 ? Ui.dp(activity, 8) : 0);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(Ui.reduced ? 90 : 220)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
            root.setBackgroundColor(0x8C000000);
            root.setAlpha(1f);
        });
    }

    void dismiss() {
        if (!dialog.isShowing()) return;
        panel.animate().alpha(0f).setDuration(120).withEndAction(() -> {
            if (dialog.isShowing()) dialog.dismiss();
        }).start();
    }

    void onDismiss(Runnable r) {
        dialog.setOnDismissListener(d -> r.run());
    }

    /** Frosted panel: a faint top-lit gradient and a hairline rim. */
    private GradientDrawable glass() {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xF22C2E36, 0xF2181A1F});
        g.setCornerRadius(Ui.dp(activity, 22));
        g.setStroke(Ui.dp(activity, 1), 0x26FFFFFF);
        return g;
    }
}
