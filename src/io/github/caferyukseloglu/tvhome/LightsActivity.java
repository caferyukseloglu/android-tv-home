package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The lights as a panel sliding in from the right, over whatever is playing. Back closes it.
 * Nothing here may be clickable but the cards: on a TV a clickable view also takes focus, and OK on it would act.
 */
public final class LightsActivity extends Activity {
    private LightsRow lights;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        int pad = Ui.dp(this, 32);
        list.setPadding(pad, Ui.dp(this, 36), pad, pad);

        TextView title = Ui.text(this, 22, Ui.TEXT, true);
        title.setText(R.string.lights);
        title.setPadding(0, 0, 0, Ui.dp(this, 18));
        list.addView(title);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.VERTICAL);
        cards.setClipChildren(false);
        list.addView(cards);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xF2111317);
        scroll.addView(list);

        FrameLayout root = new FrameLayout(this);
        root.addView(scroll, new FrameLayout.LayoutParams(Ui.dp(this, 380), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END));
        setContentView(root);
        lights = new LightsRow(this, cards, FilmMode.get(this), 316);
    }

    @Override
    protected void onResume() {
        super.onResume();
        lights.resume();
    }

    @Override
    protected void onPause() {
        lights.pause();
        super.onPause();
        finish(); // a panel, not a place: leaving it closes it
    }
}
