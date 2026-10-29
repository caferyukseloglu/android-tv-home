package io.github.caferyukseloglu.tvhome;

/** Which theme the calendar asks for. Pure Java, tested by tools/SeasonTest. */
final class Season {
    private Season() {}

    /** month 1-12. Halloween week runs Oct 24 to Nov 1, so the night after still has it. */
    static String auto(int month, int day) {
        boolean halloween = (month == 10 && day >= 24) || (month == 11 && day == 1);
        return halloween ? "halloween" : "default";
    }

    /** choice: the theme picked in settings; "auto" or null follows the calendar. */
    static String resolve(String choice, int month, int day) {
        return choice == null || choice.equals("auto") ? auto(month, day) : choice;
    }
}
