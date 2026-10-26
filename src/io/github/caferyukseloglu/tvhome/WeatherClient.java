package io.github.caferyukseloglu.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.TimeZone;

/**
 * Fetches the weather for Home and the screensaver, at most every 30 minutes. The place is found
 * once: the time zone's city when the TV has one, otherwise a coarse lookup from the network's IP
 * (the TV may only know "GMT+03:00"). Open-Meteo and the IP lookup need no account or key.
 */
final class WeatherClient {
    interface Done {
        void done(Weather weather); // null when there is no network or no place
    }

    private static final long FRESH_MS = 30 * 60 * 1000;
    private static final String TAG = "Weather";

    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());

    WeatherClient(Context context) {
        prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE);
    }

    void load(Done done) {
        Weather cached = cached();
        if (cached != null && System.currentTimeMillis() - prefs.getLong("at", 0) < FRESH_MS) {
            done.done(cached);
            return;
        }
        new Thread(() -> {
            Weather w = null;
            try {
                w = fetch();
            } catch (Exception e) {
                Log.w(TAG, "weather not loaded", e);
            }
            Weather result = w != null ? w : cached; // an old forecast beats none
            main.post(() -> done.done(result));
        }, TAG).start();
    }

    private Weather fetch() throws Exception {
        if (!prefs.contains("lat")) locate();
        JSONObject r = get("https://api.open-meteo.com/v1/forecast?latitude=" + prefs.getString("lat", "")
                + "&longitude=" + prefs.getString("lon", "")
                + "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1");
        JSONObject now = r.getJSONObject("current");
        JSONObject day = r.getJSONObject("daily");
        Weather w = new Weather((int) Math.round(now.getDouble("temperature_2m")),
                (int) Math.round(day.getJSONArray("temperature_2m_min").getDouble(0)),
                (int) Math.round(day.getJSONArray("temperature_2m_max").getDouble(0)),
                Weather.kind(now.getInt("weather_code")));
        prefs.edit().putLong("at", System.currentTimeMillis()).putInt("temp", w.temp).putInt("min", w.min)
                .putInt("max", w.max).putString("kind", w.kind.name()).apply();
        return w;
    }

    /** Once per TV: the time zone's city, else the IP's place. Stored, never looked up again. */
    private void locate() throws Exception {
        String city = Weather.city(TimeZone.getDefault().getID());
        if (city != null) {
            JSONObject hit = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(city, "UTF-8"))
                    .getJSONArray("results").getJSONObject(0);
            store(hit.getDouble("latitude"), hit.getDouble("longitude"));
            return;
        }
        JSONObject ip = get("http://ip-api.com/json/?fields=status,lat,lon");
        if (!"success".equals(ip.optString("status"))) throw new IllegalStateException("no place for this network");
        store(ip.getDouble("lat"), ip.getDouble("lon"));
    }

    private void store(double lat, double lon) {
        // Two decimals (about 1 km) are plenty for weather and keep less of the place.
        prefs.edit().putString("lat", String.format(java.util.Locale.US, "%.2f", lat))
                .putString("lon", String.format(java.util.Locale.US, "%.2f", lon)).apply();
    }

    private Weather cached() {
        if (!prefs.contains("kind")) return null;
        return new Weather(prefs.getInt("temp", 0), prefs.getInt("min", 0), prefs.getInt("max", 0),
                Weather.Kind.valueOf(prefs.getString("kind", "CLEAR")));
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(5000);
            c.setReadTimeout(8000);
            if (c.getResponseCode() != 200) throw new IllegalStateException(url + " -> " + c.getResponseCode());
            return new JSONObject(Hue.read(c.getInputStream()));
        } finally {
            c.disconnect();
        }
    }

    /** "20° Cloudy" in the TV's language. */
    static String describe(Context c, Weather w) {
        return w.temp + "°  " + c.getString(text(w.kind));
    }

    static int text(Weather.Kind k) {
        switch (k) {
            case CLEAR: return R.string.weather_clear;
            case PARTLY: return R.string.weather_partly;
            case CLOUDY: return R.string.weather_cloudy;
            case FOG: return R.string.weather_fog;
            case SNOW: return R.string.weather_snow;
            case STORM: return R.string.weather_storm;
            default: return R.string.weather_rain;
        }
    }
}
