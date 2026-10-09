package me.mrtahadarvish.astral.util;

import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DurationUtil {

    private static final Pattern PART = Pattern.compile("(\\d+)\\s*(y|mo|w|d|h|m|s)");

    private DurationUtil() {
    }

    /**
     * Parses "30d", "1d12h", "45m", "perm" ... Returns null for permanent, or throws when invalid.
     */
    public static Duration parse(String input) {
        String text = input.toLowerCase(Locale.ROOT).trim();
        if (text.equals("perm") || text.equals("permanent") || text.equals("forever") || text.equals("-1")) {
            return null;
        }
        Matcher matcher = PART.matcher(text);
        Duration total = Duration.ZERO;
        int end = 0;
        boolean found = false;
        while (matcher.find()) {
            if (matcher.start() != end) {
                throw new IllegalArgumentException(input);
            }
            end = matcher.end();
            found = true;
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "y" -> Duration.ofDays(amount * 365);
                case "mo" -> Duration.ofDays(amount * 30);
                case "w" -> Duration.ofDays(amount * 7);
                case "d" -> Duration.ofDays(amount);
                case "h" -> Duration.ofHours(amount);
                case "m" -> Duration.ofMinutes(amount);
                default -> Duration.ofSeconds(amount);
            });
        }
        if (!found || end != text.length() || total.isZero()) {
            throw new IllegalArgumentException(input);
        }
        return total;
    }

    public static boolean isDuration(String input) {
        try {
            parse(input);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String format(Duration duration) {
        if (duration == null) {
            return "Permanent";
        }
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (secs > 0 || sb.isEmpty()) {
            sb.append(secs).append("s");
        }
        return sb.toString().trim();
    }
}
