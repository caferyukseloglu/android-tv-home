package io.github.caferyukseloglu.tvhome;

/** Plain-Java test, no Android needed: ./home.sh test */
public final class SeasonTest {
    public static void main(String[] args) {
        check(Season.auto(10, 30).equals("halloween"), "Oct 30 is Halloween week");
        check(Season.auto(10, 24).equals("halloween"), "the week starts on Oct 24");
        check(Season.auto(11, 1).equals("halloween"), "Nov 1 still has it, for the night after");
        check(Season.auto(11, 2).equals("default"), "gone on Nov 2");
        check(Season.auto(10, 23).equals("default"), "not before Oct 24");
        check(Season.auto(3, 1).equals("default"), "the rest of the year is plain");
        check(Season.resolve("auto", 10, 31).equals("halloween"), "auto follows the calendar");
        check(Season.resolve("midnight", 10, 31).equals("midnight"), "a chosen theme wins over the season");
        check(Season.resolve(null, 6, 1).equals("default"), "no choice yet means auto");
        System.out.println("SeasonTest: 9 passed");
    }

    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }
}
