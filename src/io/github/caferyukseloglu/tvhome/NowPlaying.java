package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * A compact "now playing" card at the top of Home, the way the stock launcher shows one. It appears
 * only while something plays, reads the session the whole TV shares (the same source as film mode),
 * and controls it: OK plays or pauses, Left and Right skip when the app allows it.
 */
final class NowPlaying {
    private final Activity activity;
    private final ViewGroup slot; // the card is added here and removed when nothing plays
    private final MediaSessionManager sessions;
    private final ComponentName listener;
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::bind;
    private MediaController controller;
    private MediaController.Callback callback;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::tick;
    private View progressFill; // the moving part of the progress line, null when the track has no length

    NowPlaying(Activity activity, ViewGroup slot) {
        this.activity = activity;
        this.slot = slot;
        this.sessions = activity.getSystemService(MediaSessionManager.class);
        this.listener = new ComponentName(activity, MediaWatch.class);
    }

    void resume() {
        main.post(tick);
        try {
            sessions.addOnActiveSessionsChangedListener(changed, listener);
            bind(sessions.getActiveSessions(listener));
        } catch (SecurityException noPermission) {
            // MediaWatch is not enabled as a notification listener yet; the card simply stays hidden.
        }
    }

    void pause() {
        main.removeCallbacks(tick);
        slot.removeAllViews(); // the bars stop with their card; resume draws it again from the live session
        try {
            sessions.removeOnActiveSessionsChangedListener(changed);
        } catch (Exception ignored) {
            // already removed
        }
        watch(null);
    }

    /** Follow the playing session, or the most recent one; redraw whenever it changes. */
    private void bind(List<MediaController> controllers) {
        MediaController best = null;
        if (controllers != null) {
            for (MediaController c : controllers) {
                PlaybackState s = c.getPlaybackState();
                if (s == null) continue;
                if (s.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
                if (best == null && hasContent(c)) best = c;
            }
        }
        watch(best);
        render();
    }

    private void watch(MediaController next) {
        if (controller != null && callback != null) controller.unregisterCallback(callback);
        controller = next;
        if (controller == null) {
            callback = null;
            return;
        }
        callback = new MediaController.Callback() {
            @Override public void onPlaybackStateChanged(PlaybackState state) {
                render();
            }

            @Override public void onMetadataChanged(MediaMetadata metadata) {
                render();
            }

            @Override public void onSessionDestroyed() {
                bind(sessions.getActiveSessions(listener));
            }
        };
        controller.registerCallback(callback);
    }

    private void render() {
        boolean hadFocus = slot.getChildCount() > 0 && slot.getChildAt(0).hasFocus();
        slot.removeAllViews();
        if (controller == null || !hasContent(controller)) return;
        MediaMetadata m = controller.getMetadata();
        boolean playing = controller.getPlaybackState() != null
                && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING;

        LinearLayout card = new LinearLayout(activity);
        card.setGravity(Gravity.CENTER_VERTICAL);
        int pad = Ui.dp(activity, 12);
        card.setPadding(pad, 0, Ui.dp(activity, 22), 0);

        Bitmap art = m == null ? null : MediaWatch.firstBitmap(m);
        card.addView(cover(art), new LinearLayout.LayoutParams(Ui.dp(activity, 50), Ui.dp(activity, 50)));

        LinearLayout texts = new LinearLayout(activity);
        texts.setOrientation(LinearLayout.VERTICAL);
        TextView title = Ui.text(activity, 16, Ui.TEXT, true);
        title.setText(text(m, MediaMetadata.METADATA_KEY_TITLE, activity.getString(R.string.now_playing)));
        TextView sub = Ui.text(activity, 13, Ui.TEXT_DIM, false);
        String artist = text(m, MediaMetadata.METADATA_KEY_ARTIST, null);
        // Some apps (Spotify on TV) repeat the title as the artist: then the app's name says more.
        sub.setText(artist == null || artist.equals(title.getText().toString()) ? appName(controller.getPackageName()) : artist);
        texts.addView(title);
        texts.addView(sub);
        texts.addView(progress(m));
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        tp.leftMargin = Ui.dp(activity, 14);
        card.addView(texts, tp);

        Equalizer bars = new Equalizer(activity);
        bars.setPlaying(playing);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(activity, 22), Ui.dp(activity, 20));
        bp.leftMargin = Ui.dp(activity, 18);
        card.addView(bars, bp);

        FrameLayout wrap = new FrameLayout(activity);
        wrap.addView(card);
        wrap.setFocusable(true);
        Ui.focusable(wrap, tint(art), null, 1.03f);
        wrap.setRevealOnFocusHint(false);
        wrap.setOnClickListener(v -> {
            if (playing) controller.getTransportControls().pause();
            else controller.getTransportControls().play();
        });
        wrap.setOnKeyListener((v, code, event) -> skip(code, event));
        slot.addView(wrap, new ViewGroup.LayoutParams(Ui.dp(activity, 400), Ui.dp(activity, 70)));
        if (hadFocus) wrap.requestFocus();
    }

    /** Left/Right skip, but only when the app offers it, so focus can still leave the card otherwise. */
    private boolean skip(int code, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        PlaybackState s = controller.getPlaybackState();
        long actions = s == null ? 0 : s.getActions();
        if (code == KeyEvent.KEYCODE_DPAD_RIGHT && (actions & PlaybackState.ACTION_SKIP_TO_NEXT) != 0) {
            controller.getTransportControls().skipToNext();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_LEFT && (actions & PlaybackState.ACTION_SKIP_TO_PREVIOUS) != 0) {
            controller.getTransportControls().skipToPrevious();
            return true;
        }
        return false;
    }

    private View cover(Bitmap b) {
        ImageView art = new ImageView(activity);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setClipToOutline(true);
        art.setBackground(Ui.rounded(activity, Ui.CARD));
        if (b != null) art.setImageDrawable(new BitmapDrawable(activity.getResources(), b));
        return art;
    }

    /** The card glows in the cover's color from the left, fading into glass. */
    private GradientDrawable tint(Bitmap art) {
        int accent = -1;
        if (art != null) {
            Bitmap small = Bitmap.createScaledBitmap(art, 24, 24, true);
            int[] px = new int[24 * 24];
            small.getPixels(px, 0, 24, 0, 0, 24, 24);
            accent = Colors.accent(px);
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                accent == -1 ? new int[] {0x2EFFFFFF, 0x1AFFFFFF} : new int[] {0x73000000 | accent, 0x1AFFFFFF});
        bg.setCornerRadius(Ui.dp(activity, 18));
        return bg;
    }

    /** A thin line under the title that fills as the track plays; empty for streams without a length. */
    private View progress(MediaMetadata m) {
        FrameLayout track = new FrameLayout(activity);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(activity, 3));
        lp.topMargin = Ui.dp(activity, 8);
        track.setLayoutParams(lp);
        long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        progressFill = null;
        if (duration <= 0) return track;
        GradientDrawable rail = new GradientDrawable();
        rail.setColor(0x33FFFFFF);
        rail.setCornerRadius(Ui.dp(activity, 2));
        track.setBackground(rail);
        View fill = new View(activity);
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(0xE6FFFFFF);
        bar.setCornerRadius(Ui.dp(activity, 2));
        fill.setBackground(bar);
        fill.setPivotX(0);
        fill.setScaleX(0);
        track.addView(fill, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        progressFill = fill;
        tick();
        return track;
    }

    /** Twice a second: where the track is, from the last reported position and the time since. */
    private void tick() {
        main.removeCallbacks(tick);
        if (progressFill != null && controller != null && controller.getPlaybackState() != null && controller.getMetadata() != null) {
            PlaybackState s = controller.getPlaybackState();
            long duration = controller.getMetadata().getLong(MediaMetadata.METADATA_KEY_DURATION);
            long at = s.getPosition();
            if (s.getState() == PlaybackState.STATE_PLAYING) {
                at += (long) ((SystemClock.elapsedRealtime() - s.getLastPositionUpdateTime()) * s.getPlaybackSpeed());
            }
            if (duration > 0) progressFill.setScaleX(Math.max(0f, Math.min(1f, at / (float) duration)));
        }
        main.postDelayed(tick, 500);
    }

    private static boolean hasContent(MediaController c) {
        PlaybackState s = c.getPlaybackState();
        int state = s == null ? 0 : s.getState();
        return c.getMetadata() != null && state != PlaybackState.STATE_NONE
                && state != PlaybackState.STATE_ERROR && state != PlaybackState.STATE_STOPPED;
    }

    private static String text(MediaMetadata m, String key, String fallback) {
        String v = m == null ? null : m.getString(key);
        return v == null || v.isEmpty() ? fallback : v;
    }

    private String appName(String pkg) {
        try {
            return activity.getPackageManager().getApplicationLabel(
                    activity.getPackageManager().getApplicationInfo(pkg, 0)).toString();
        } catch (Exception gone) {
            return pkg;
        }
    }
}
