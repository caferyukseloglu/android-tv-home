package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * The apps as a tvOS-style grid: five across, the name fading in under the focused card only.
 * Hold OK for Move / Hide; while moving, the arrows carry the card through the grid.
 */
final class AppGrid {
    static final int COLUMNS = 5;
    private static final int CARD_W = 152; // dp: five cards and four gaps fill the 840 dp between the margins
    private static final int CARD_H = 86;
    private static final int GAP = 20;

    private final Activity activity;
    private final Apps apps;
    private final FilmMode film;
    private final LinearLayout grid;
    private final List<View> cells = new ArrayList<>(); // one per app, in grid order; the card is child 0
    private View moving; // the cell being moved, null when not moving

    AppGrid(Activity activity, Apps apps, FilmMode film) {
        this.activity = activity;
        this.apps = apps;
        this.film = film;
        grid = new LinearLayout(activity);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
    }

    View view() {
        return grid;
    }

    void show(String focusPkg) {
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
    }

    /** Rows of five; called again after a move, the cells keep their views and focus. */
    private void layout() {
        grid.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < cells.size(); i++) {
            View cell = cells.get(i);
            if (cell.getParent() != null) ((ViewGroup) cell.getParent()).removeView(cell);
            if (i % COLUMNS == 0) {
                row = new LinearLayout(activity);
                row.setClipChildren(false);
                grid.addView(row);
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

        TextView name = Ui.text(activity, 14, Ui.TEXT, false);
        name.setText(app.hidden ? activity.getString(R.string.hidden) + " · " + app.label : app.label);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        name.setAlpha(0f);
        name.setPadding(0, Ui.dp(activity, 12), 0, 0);

        Ui.focusable(card, Ui.rounded(activity, Ui.CARD), (v, has) -> name.animate().alpha(has ? 1f : 0f).setDuration(160).start());
        card.setOnClickListener(v -> {
            film.start(app.pkg);
            apps.launch(app);
        });
        card.setOnLongClickListener(v -> {
            menu(app);
            return true;
        });

        LinearLayout cell = new LinearLayout(activity);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setClipChildren(false);
        cell.addView(card, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, CARD_H)));
        cell.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 34)));
        return cell;
    }

    private static View card(View cell) {
        return ((ViewGroup) cell).getChildAt(0);
    }

    private static Apps.App app(View cell) {
        return (Apps.App) card(cell).getTag();
    }

    /** Hold OK on an app: move it, or hide/show it. */
    private void menu(Apps.App app) {
        Dialog menu = new Dialog(activity, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        int pad = Ui.dp(activity, 20);
        list.setPadding(pad, pad, pad, pad);
        list.setBackground(Ui.rounded(activity, 0xFF17191E));
        TextView title = Ui.text(activity, 20, Ui.TEXT, true);
        title.setText(app.label);
        title.setPadding(Ui.dp(activity, 4), 0, 0, Ui.dp(activity, 8));
        list.addView(title);
        if (!app.hidden) list.addView(menuItem(R.string.move, () -> {
            menu.dismiss();
            startMove(app.pkg);
        }));
        list.addView(menuItem(app.hidden ? R.string.show : R.string.hide, () -> {
            menu.dismiss();
            apps.toggleHidden(app.pkg);
            show(app.pkg);
        }));
        if (apps.removable(app.pkg)) list.addView(menuItem(R.string.uninstall, () -> {
            menu.dismiss();
            // Android's own uninstall prompt; Home reloads the grid when it comes back.
            activity.startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + app.pkg)));
        }));
        menu.setContentView(list, new ViewGroup.LayoutParams(Ui.dp(activity, 320), ViewGroup.LayoutParams.WRAP_CONTENT));
        menu.getWindow().setBackgroundDrawable(new ColorDrawable(0));
        menu.show();
    }

    private View menuItem(int text, Runnable action) {
        TextView item = Ui.text(activity, 17, Ui.TEXT, false);
        item.setText(text);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(Ui.dp(activity, 16), 0, Ui.dp(activity, 16), 0);
        Ui.focusable(item, Ui.rounded(activity, Ui.CARD), null, 1f);
        item.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 48));
        p.topMargin = Ui.dp(activity, 8);
        item.setLayoutParams(p);
        return item;
    }

    /** The card lifts; the arrows carry it through the grid, OK or Back puts it down and saves the order. */
    private void startMove(String pkg) {
        for (View cell : cells) {
            if (app(cell).pkg.equals(pkg)) moving = cell;
        }
        if (moving == null) return;
        card(moving).requestFocus();
        card(moving).animate().translationY(-Ui.dp(activity, 10)).setDuration(140).start();
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
            layout();
            card(moving).requestFocus();
        }
        return true;
    }

    private void endMove() {
        card(moving).animate().translationY(0).setDuration(140).start();
        moving = null;
        List<String> order = new ArrayList<>();
        for (View cell : cells) order.add(app(cell).pkg);
        apps.saveOrder(order);
    }
}
