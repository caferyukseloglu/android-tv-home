package io.github.caferyukseloglu.tvhome;

/**
 * Weather from Open-Meteo: free, no account, no key. The place comes from the TV's time zone
 * ("Europe/Istanbul" -> Istanbul), so no location permission is needed. Pure parts are tested by
 * tools/WeatherTest; WeatherClient does the network.
 */
final class Weather {
    enum Kind { CLEAR, PARTLY, CLOUDY, FOG, RAIN, SNOW, STORM }

    final int temp;
    final int min;
    final int max;
    final Kind kind;

    Weather(int temp, int min, int max, Kind kind) {
        this.temp = temp;
        this.min = min;
        this.max = max;
        this.kind = kind;
    }

    /** The city part of an IANA time zone id, or null for zones without one (UTC, Etc/GMT+3). */
    static String city(String zoneId) {
        int slash = zoneId == null ? -1 : zoneId.lastIndexOf('/');
        if (slash < 0 || zoneId.startsWith("Etc/")) return null;
        return zoneId.substring(slash + 1).replace('_', ' ');
    }

    /** WMO weather interpretation codes, as Open-Meteo returns them. */
    static Kind kind(int code) {
        if (code == 0) return Kind.CLEAR;
        if (code <= 2) return Kind.PARTLY;
        if (code == 3) return Kind.CLOUDY;
        if (code == 45 || code == 48) return Kind.FOG;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return Kind.SNOW;
        if (code >= 95) return Kind.STORM;
        return Kind.RAIN; // drizzle 51-57, rain 61-67, showers 80-82
    }
}
