package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The apps as a tvOS-style grid: five across, the first row in a dock band. The focused app's name
 * is not under the card but in Home's large title, through {@link Host}.
 * Hold OK (or Menu) for a glass menu under the card. Move lifts the card and dims the others; the
 * arrows carry it through the grid while the page holds still around it.
 */
final class AppGrid {
    interface Host {
        /** accent: the banner's brand color as 0xRRGGBB, or -1 when it has none; dock: the card is in the first row. */
        void focused(Apps.App app, int accent, boolean dock);

        /** Move mode on or off, for the hint at the bottom of the screen. */
        void moving(boolean on);

        /** Runs a re-layout of the grid without letting the page scroll away (REA: their keepScroll). */
        void keepScroll(Runnable relayout);
    }

    static final int COLUMNS = 5;
    private static final int CARD_W = 152; // dp: five cards and four gaps fill the 840 dp between the margins
    private static final int CARD_H = 86;
    private static final int GAP = 20;
    private static final int DOCK_PAD = 14; // dp of band around the first row
    private static final int HINT_ROOM = 96; // dp kept clear under a moving card, where the hint bar sits

    private final Activity activity;
    private final Apps apps;
    private final Host host;
    private final Map<String, Integer> accents = new HashMap<>(); // per package, decoded once
    private final LinearLayout grid;
    private final List<View> cells = new ArrayList<>(); // one per app, in grid order; the card is child 0
    private View moving; // the cell being moved, null when not moving
    private boolean relayout; // focus lands on stray cards while rows are rebuilt: ignore it
    private boolean revealed;

    AppGrid(Activity activity, Apps apps, Host host) {
        this.activity = activity;
        this.apps = apps;
        this.host = host;
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
    }

    View view() {
        return grid;
    }

    boolean isMoving() {
        return moving != null;
    }

    void show(String focusPkg) {
        if (moving != null) host.moving(false);
        moving = null; // the cards are rebuilt: a move in progress would point at a card that is gone
        cells.clear();
        View focus = null;
        for (Apps.App app : apps.load()) {
            View cell = cell(app);
            cells.add(cell);
            if (app.pkg.equals(focusPkg)) focus = card(cell);
        }
        layout();
        if (focus == null && !cells.isEmpty()) focus = card(cells.get(0));
        if (focus != null) focus.requestFocus();
        if (!revealed) reveal();
    }

    /** First time Home appears: the cards rise into place one after another. */
    private void reveal() {
        revealed = true;
        for (int i = 0; i < cells.size(); i++) {
            View cell = cells.get(i);
            cell.setAlpha(0f);
            cell.setTranslationY(Ui.dp(activity, 24));
            cell.animate().alpha(1f).translationY(0).setStartDelay(60 + i * 22L).setDuration(460)
                    .setInterpolator(Ui.SETTLE).start();
        }
    }

    /** Rows of five, the first in the dock band; called again after a move, the cells keep their views and focus. */
    private void layout() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            View cell = cells.get(i);
            if (cell.getParent() != null) ((ViewGroup) cell.getParent()).removeView(cell);
            if (i % COLUMNS == 0) {
                row = new LinearLayout(activity);
                row.setClipChildren(false);
                LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rp.bottomMargin = Ui.dp(activity, GAP);
                if (i == 0) { // the band reaches past the cards, so the dock's cards line up with the rows below
                    int pad = Ui.dp(activity, DOCK_PAD);
                    row.setPadding(pad, pad, pad, pad);
                    row.setClipToPadding(false); // the focus zoom grows into the band
                    rp.leftMargin = rp.rightMargin = -pad;
                    rp.bottomMargin = Ui.dp(activity, GAP + 8);
                    row.setBackground(dockBand());
                }
                grid.addView(row, rp);
            }
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(Ui.dp(activity, CARD_W), ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i % COLUMNS != COLUMNS - 1) p.rightMargin = Ui.dp(activity, GAP);
            row.addView(cell, p);
        }
    }

    private View cell(Apps.App app) {
        FrameLayout card = new FrameLayout(activity);
        if (app.banner != null) {
            ImageView art = new ImageView(activity);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(app.banner);
            card.addView(art, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            ImageView icon = new ImageView(activity);
            icon.setImageDrawable(app.icon);
            card.addView(icon, new FrameLayout.LayoutParams(Ui.dp(activity, 56), Ui.dp(activity, 56), Gravity.CENTER));
        }
        card.setAlpha(app.hidden ? 0.35f : 1f);
        card.setContentDescription(app.label);
        card.setTag(app);
        View sheen = sheen();
        card.addView(sheen, new FrameLayout.LayoutParams(Ui.dp(activity, 90), ViewGroup.LayoutParams.MATCH_PARENT));

        Ui.focusable(card, Ui.rounded(activity, Ui.CARD), (v, has) -> {
            if (has && !relayout) { // scroll so a margin under the card shows too, room for the focus zoom
                View cell = (View) v.getParent();
                cell.requestRectangleOnScreen(new Rect(0, 0, cell.getWidth(), cell.getHeight() + Ui.dp(activity, GAP)), false);
                host.focused(app, accent(app), cells.indexOf(cell) < COLUMNS);
            }
            if (has && !relayout && moving == null) { // one glint across the banner, like light catching glass
                sheen.setTranslationX(-Ui.dp(activity, 100));
                sheen.animate().translationX(Ui.dp(activity, CARD_W + 20)).setStartDelay(90).setDuration(700)
                        .setInterpolator(Ui.SETTLE).start();
            }
        });
        card.setRevealOnFocusHint(false); // Home glides to the card itself (MainActivity.Glide)
        card.setOnClickListener(v -> apps.launch(app));
        card.setOnLongClickListener(v -> {
            if (moving == null) menu(app, v);
            return true;
        });

        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, CARD_H)));
        return cell;
    }

    /** A faint glass band behind the first row, where Apple TV puts the dock. */
    private GradientDrawable dockBand() {
        GradientDrawable band = new GradientDrawable();
        band.setColor(0x14FFFFFF);
        band.setStroke(Ui.dp(activity, 1), 0x1FFFFFFF);
        band.setCornerRadius(Ui.dp(activity, 14 + DOCK_PAD));
        return band;
    }

    /** The banner (or icon) drawn small, then {@link Colors#accent}. A copy is drawn, so the card's own drawable keeps its bounds. */
    private int accent(Apps.App app) {
        Integer cached = accents.get(app.pkg);
        if (cached != null) return cached;
        Drawable art = app.banner != null ? app.banner : app.icon;
        Drawable copy = art.getConstantState() != null ? art.getConstantState().newDrawable().mutate() : art;
        int w = 32, h = 18;
        Bitmap small = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        copy.setBounds(0, 0, w, h);
        copy.draw(new Canvas(small));
        int[] px = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        small.recycle();
        int accent = Colors.accent(px);
        accents.put(app.pkg, accent);
        return accent;
    }

    /** A soft diagonal band of light, parked off the card until focus sends it across. */
    private View sheen() {
        GradientDrawable band = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[] {0x00FFFFFF, 0x38FFFFFF, 0x00FFFFFF});
        View v = new View(activity);
        v.setBackground(band);
        v.setRotation(14f);
        v.setScaleY(1.6f);
        v.setTranslationX(-Ui.dp(activity, 100));
        return v;
    }

    private static View card(View cell) {
        return ((ViewGroup) cell).getChildAt(0);
    }

    private static Apps.App app(View cell) {
        return (Apps.App) card(cell).getTag();
    }

    /** Hold OK on an app: a glass menu under its card. */
    private void menu(Apps.App app, View card) {
        Sheet menu = new Sheet(activity, app.label);
        if (!app.hidden) menu.add(activity.getString(R.string.move), () -> {
            menu.dismiss();
            startMove(app.pkg);
        });
        menu.add(activity.getString(app.hidden ? R.string.show : R.string.hide), () -> {
            menu.dismiss();
            apps.toggleHidden(app.pkg);
            show(app.pkg);
        });
        menu.add(activity.getString(R.string.close_app), () -> {
            menu.dismiss();
            Recents recents = new Recents(activity);
            recents.close(java.util.Collections.singletonList(app.pkg), () -> recents.info(app.pkg));
        });
        menu.add(activity.getString(R.string.app_info), () -> {
            menu.dismiss();
            activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)));
        });
        if (apps.removable(app.pkg)) menu.add(activity.getString(R.string.uninstall), () -> {
            menu.dismiss();
            // Android's own uninstall prompt; Home reloads the grid when it comes back.
            activity.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)));
        });
        menu.showAt(card, 300);
    }

    /** The card lifts, the others dim; the arrows carry it, OK or Back puts it down and saves the order. */
    private void startMove(String pkg) {
        for (View cell : cells) {
            if (app(cell).pkg.equals(pkg)) moving = cell;
        }
        if (moving == null) return;
        for (View cell : cells) cell.animate().alpha(cell == moving ? 1f : 0.4f).setDuration(180).start();
        card(moving).requestFocus();
        card(moving).animate().translationY(-Ui.dp(activity, 12)).setDuration(160).start();
        host.moving(true);
    }

    /** True when the key was used for moving; the activity then passes nothing on. */
    boolean onKey(KeyEvent event) {
        if (moving == null) return false;
        // One step per press: a held key repeats ~20 times a second, far too fast to follow.
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        int from = cells.indexOf(moving);
        int to;
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_DPAD_LEFT: to = from - 1; break;
            case KeyEvent.KEYCODE_DPAD_RIGHT: to = from + 1; break;
            case KeyEvent.KEYCODE_DPAD_UP: to = from - COLUMNS; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: to = from + COLUMNS; break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                endMove();
                return true;
            default:
                return true; // while moving, no other key reaches the screen
        }
        if (to >= 0 && to < cells.size() && !app(cells.get(to)).hidden) { // hidden apps stay at the end
            cells.remove(from);
            cells.add(to, moving);
            host.keepScroll(() -> {
                relayout = true;
                layout();
                card(moving).requestFocus();
                relayout = false;
            });
            // Now the page may follow the card, smoothly, as far as it needs to.
            View cell = moving;
            cell.post(() -> cell.requestRectangleOnScreen(new Rect(0, 0, cell.getWidth(), cell.getHeight() + Ui.dp(activity, HINT_ROOM)), false));
        }
        return true;
    }

    private void endMove() {
        card(moving).animate().translationY(0).setDuration(160).start();
        for (View cell : cells) cell.animate().alpha(1f).setDuration(180).start();
        moving = null;
        host.moving(false);
        List<String> order = new ArrayList<>();
        for (View cell : cells) order.add(app(cell).pkg);
        apps.saveOrder(order);
    }
}
