package io.github.caferyukseloglu.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Apps used lately, to close the ones left running in the background, with the TV's memory on top.
 * The list comes from usage stats (granted by `./home.sh install`). Closing stops the app's media
 * session if it plays, asks Android to kill its background processes, then opens the app's own
 * info page, where Force stop is one press: some TVs (this TCL, measured) ignore the kill request
 * from an ordinary app, and only the system's Force stop is sure to end it.
 */
final class Recents {
    private static final long WINDOW_MS = 12 * 3600_000L; // apps used in the last 12 hours
    private final Activity activity;
    private final PackageManager pm;
    private final ActivityManager am;
    private final SharedPreferences closed;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Sheet sheet;

    Recents(Activity activity) {
        this.activity = activity;
        pm = activity.getPackageManager();
        am = activity.getSystemService(ActivityManager.class);
        closed = activity.getSharedPreferences("closed", Context.MODE_PRIVATE);
        sheet = new Sheet(activity, null);
    }

    void show() {
        fill();
        sheet.showCentered(560);
    }

    private void fill() {
        sheet.clear();
        sheet.setTitle(memory());
        List<String> apps = recent();
        if (!allowed()) {
            sheet.add(activity.getString(R.string.recents_permission), () -> sheet.dismiss());
            return;
        }
        if (apps.isEmpty()) {
            sheet.add(activity.getString(R.string.recents_none), () -> sheet.dismiss());
            return;
        }
        sheet.add(null, activity.getString(R.string.close_all), String.valueOf(apps.size()), () -> close(apps, () -> {
            fill();
            sheet.focusFirst();
        }));
        for (String pkg : apps) {
            sheet.add(icon(pkg), label(pkg), activity.getString(R.string.force_stop), () -> close(java.util.Collections.singletonList(pkg), () -> {
                sheet.dismiss();
                info(pkg);
            }));
        }
    }

    /** The system's page for the app: Force stop is its first button. */
    void info(String pkg) {
        activity.startActivity(new android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                android.net.Uri.parse("package:" + pkg)));
    }

    /** Stops what the apps play and asks Android to end them; done runs when that is through. */
    void close(List<String> pkgs, Runnable done) {
        for (String pkg : pkgs) {
            stopMedia(pkg);
            closed.edit().putLong(pkg, System.currentTimeMillis()).apply();
        }
        // The media apps need a moment to drop their foreground service before their process can go.
        main.postDelayed(() -> {
            for (String pkg : pkgs) am.killBackgroundProcesses(pkg);
            main.postDelayed(done, 300);
        }, 500);
    }

    private void stopMedia(String pkg) {
        try {
            MediaSessionManager sessions = activity.getSystemService(MediaSessionManager.class);
            for (MediaController c : sessions.getActiveSessions(new ComponentName(activity, MediaWatch.class))) {
                if (c.getPackageName().equals(pkg)) c.getTransportControls().stop();
            }
        } catch (SecurityException noListener) {
            // without notification access there is no session to stop; killing alone still works for most apps
        }
    }

    /** Launchable apps used in the window, newest first, minus the ones closed since. */
    private List<String> recent() {
        List<String> out = new ArrayList<>();
        UsageStatsManager usage = activity.getSystemService(UsageStatsManager.class);
        long now = System.currentTimeMillis();
        List<UsageStats> stats = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - WINDOW_MS, now);
        if (stats == null) return out;
        stats.sort((a, b) -> Long.compare(b.getLastTimeUsed(), a.getLastTimeUsed()));
        for (UsageStats s : stats) {
            String pkg = s.getPackageName();
            if (s.getLastTimeUsed() < now - WINDOW_MS || s.getTotalTimeInForeground() <= 0) continue;
            if (pkg.equals(activity.getPackageName()) || out.contains(pkg)) continue;
            if (s.getLastTimeUsed() <= closed.getLong(pkg, 0)) continue;
            if (pm.getLeanbackLaunchIntentForPackage(pkg) == null) continue; // system parts and other launchers
            out.add(pkg);
            if (out.size() == 8) break;
        }
        return out;
    }

    private boolean allowed() {
        AppOpsManager ops = activity.getSystemService(AppOpsManager.class);
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), activity.getPackageName())
                == AppOpsManager.MODE_ALLOWED;
    }

    private String memory() {
        ActivityManager.MemoryInfo m = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(m);
        return String.format(Locale.getDefault(), activity.getString(R.string.ram_used),
                (m.totalMem - m.availMem) / 1073741824.0, m.totalMem / 1073741824.0);
    }


    private CharSequence label(String pkg) {
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
        } catch (PackageManager.NameNotFoundException gone) {
            return pkg;
        }
    }

    private android.graphics.drawable.Drawable icon(String pkg) {
        try {
            return pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException gone) {
            return null;
        }
    }
}
