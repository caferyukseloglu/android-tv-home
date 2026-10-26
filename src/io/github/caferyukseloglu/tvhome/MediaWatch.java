package io.github.caferyukseloglu.tvhome;

import android.content.ComponentName;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.service.notification.NotificationListenerService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Watches what is playing (title and cover art, the same data the TV's "now playing" uses) so
 * film mode can color the strip from the cover. A notification listener is the only way Android
 * gives an app other apps' media sessions; no notification is read: the notification callbacks
 * are left empty. Turned on by `./home.sh install`.
 */
public final class MediaWatch extends NotificationListenerService {
    private final Map<MediaController, MediaController.Callback> watched = new HashMap<>();
    private final MediaSessionManager.OnActiveSessionsChangedListener changed = this::watch;
    private MediaSessionManager sessions;
    private FilmMode film;

    @Override
    public void onListenerConnected() {
        film = new FilmMode(this);
        sessions = getSystemService(MediaSessionManager.class);
        ComponentName me = new ComponentName(this, MediaWatch.class);
        sessions.addOnActiveSessionsChangedListener(changed, me);
        watch(sessions.getActiveSessions(me));
    }

    @Override
    public void onListenerDisconnected() {
        if (sessions != null) sessions.removeOnActiveSessionsChangedListener(changed);
        watch(null);
    }

    private void watch(List<MediaController> controllers) {
        for (Map.Entry<MediaController, MediaController.Callback> e : watched.entrySet()) {
            e.getKey().unregisterCallback(e.getValue());
        }
        watched.clear();
        if (controllers == null) return;
        for (MediaController c : controllers) {
            MediaController.Callback cb = new MediaController.Callback() {
                @Override public void onMetadataChanged(MediaMetadata metadata) {
                    check(c);
                }

                @Override public void onPlaybackStateChanged(PlaybackState state) {
                    check(c);
                }
            };
            c.registerCallback(cb);
            watched.put(c, cb);
            check(c);
        }
    }

    private void check(MediaController c) {
        PlaybackState state = c.getPlaybackState();
        MediaMetadata m = c.getMetadata();
        if (state == null || state.getState() != PlaybackState.STATE_PLAYING || m == null) return;
        String key = c.getPackageName() + "|" + m.getString(MediaMetadata.METADATA_KEY_TITLE);
        if (key.equals(FilmMode.shown)) return;
        FilmMode.shown = key;
        Bitmap art = firstBitmap(m, MediaMetadata.METADATA_KEY_ART, MediaMetadata.METADATA_KEY_ALBUM_ART,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        String uri = firstString(m, MediaMetadata.METADATA_KEY_ART_URI, MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI);
        film.content(c.getPackageName(), art, uri);
    }

    private static Bitmap firstBitmap(MediaMetadata m, String... keys) {
        for (String k : keys) {
            Bitmap b = m.getBitmap(k);
            if (b != null) return b;
        }
        return null;
    }

    private static String firstString(MediaMetadata m, String... keys) {
        for (String k : keys) {
            String s = m.getString(k);
            if (s != null && !s.isEmpty()) return s;
        }
        return null;
    }
}
