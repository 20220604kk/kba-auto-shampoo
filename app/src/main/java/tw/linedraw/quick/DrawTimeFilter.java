package tw.linedraw.quick;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The source's first clock time is the opening time, not the coupon expiry. */
final class DrawTimeFilter {
    static final String UNSPECIFIED = "未標時間";
    static final List<String> ALLOWED_TIMES = Collections.unmodifiableList(
            Arrays.asList("10:00", "10:30", "11:00", "12:00"));
    private static final Pattern CLOCK = Pattern.compile("(?:^|[^0-9])([0-9]{1,2}):([0-9]{2})(?![0-9])");

    private DrawTimeFilter() {}

    static String openingTime(String period) {
        if (period == null || period.isEmpty() || period.contains("原文未註明時間") ||
                period.contains("即日起")) return UNSPECIFIED;
        Matcher match = CLOCK.matcher(period);
        if (!match.find()) return UNSPECIFIED;
        int hour = Integer.parseInt(match.group(1));
        int minute = Integer.parseInt(match.group(2));
        if (hour > 23 || minute > 59) return UNSPECIFIED;
        String time = String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute);
        return ALLOWED_TIMES.contains(time) ? time : UNSPECIFIED;
    }
}
