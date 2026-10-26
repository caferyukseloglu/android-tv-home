package io.github.caferyukseloglu.tvhome;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class WeatherTest {
    public static void main(String[] args) {
        check("Istanbul".equals(Weather.city("Europe/Istanbul")), "city from a time zone");
        check("New York".equals(Weather.city("America/New_York")), "underscores become spaces");
        check("Buenos Aires".equals(Weather.city("America/Argentina/Buenos_Aires")), "the last part is the city");
        check(Weather.city("UTC") == null, "no city in a bare zone");
        check(Weather.kind(0) == Weather.Kind.CLEAR, "code 0 is clear");
        check(Weather.kind(3) == Weather.Kind.CLOUDY, "code 3 is cloudy");
        check(Weather.kind(63) == Weather.Kind.RAIN, "code 63 is rain");
        check(Weather.kind(75) == Weather.Kind.SNOW, "code 75 is snow");
        check(Weather.kind(95) == Weather.Kind.STORM, "code 95 is a storm");
        check(Weather.kind(45) == Weather.Kind.FOG, "code 45 is fog");
        System.out.println("WeatherTest: 10 passed");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
