package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The TV's launchable apps, in the order the user arranged, with hidden apps kept on the TV. */
final class Apps {
    static final class App {
        final String pkg;
        final CharSequence label;
        final Drawable banner; // null when the app ships none; the card then shows its icon
        final Drawable icon;
        final boolean hidden;

        App(String pkg, CharSequence label, Drawable banner, Drawable icon, boolean hidden) {
            this.pkg = pkg;
            this.label = label;
            this.banner = banner;
            this.icon = icon;
            this.hidden = hidden;
        }
    }

    private final Context context;
    private final PackageManager pm;
    private final SharedPreferences prefs;
    // Banners decoded once per app, not on every return to Home.
    // shortcut: an app update with new artwork shows the old banner until the launcher restarts.
    private final Map<String, App> artwork = new HashMap<>();

    Apps(Context context) {
        this.context = context;
        this.pm = context.getPackageManager();
        this.prefs = context.getSharedPreferences("apps", Context.MODE_PRIVATE);
    }

    List<App> load() {
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(query, 0);
        found.sort(new ResolveInfo.DisplayNameComparator(pm));
        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (ResolveInfo r : found) {
            if (!r.activityInfo.packageName.equals(context.getPackageName())) {
                byPkg.putIfAbsent(r.activityInfo.packageName, r);
            }
        }
        Set<String> hidden = hidden();
        List<App> apps = new ArrayList<>();
        for (String pkg : AppOrder.order(new ArrayList<>(byPkg.keySet()), savedOrder(), hidden)) {
            App art = artwork.get(pkg);
            if (art == null) {
                ResolveInfo r = byPkg.get(pkg);
                Drawable banner = r.activityInfo.loadBanner(pm);
                if (banner == null) banner = r.activityInfo.applicationInfo.loadBanner(pm);
                art = new App(pkg, r.loadLabel(pm), banner, r.loadIcon(pm), false);
                artwork.put(pkg, art);
            }
            apps.add(new App(pkg, art.label, art.banner, art.icon, hidden.contains(pkg)));
        }
        return apps;
    }

    void launch(App app) {
        Intent intent = pm.getLeanbackLaunchIntentForPackage(app.pkg);
        if (intent == null) return; // uninstalled since the row was drawn
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    void toggleHidden(String pkg) {
        Set<String> next = new HashSet<>(hidden());
        if (!next.remove(pkg)) next.add(pkg);
        prefs.edit().putStringSet("hidden", next).apply();
    }

    /** The row as the user arranged it, left to right. */
    void saveOrder(List<String> pkgs) {
        prefs.edit().putString("order", String.join(",", pkgs)).apply();
    }

    private List<String> savedOrder() {
        String order = prefs.getString("order", "");
        return order.isEmpty() ? Collections.<String>emptyList() : Arrays.asList(order.split(","));
    }

    private Set<String> hidden() {
        return prefs.getStringSet("hidden", Collections.<String>emptySet());
    }
}
