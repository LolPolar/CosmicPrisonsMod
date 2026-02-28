package me.landon.papercompanion.util;

import java.util.Locale;

public final class AmountFormatter {
    private AmountFormatter() {}

    public static String compact(long amount) {
        long absolute = Math.abs(amount);
        if (absolute < 1_000L) {
            return Long.toString(amount);
        }

        double value = amount;
        String suffix = "";
        if (absolute >= 1_000_000_000_000L) {
            value /= 1_000_000_000_000.0D;
            suffix = "t";
        } else if (absolute >= 1_000_000_000L) {
            value /= 1_000_000_000.0D;
            suffix = "b";
        } else if (absolute >= 1_000_000L) {
            value /= 1_000_000.0D;
            suffix = "m";
        } else {
            value /= 1_000.0D;
            suffix = "k";
        }

        if (Math.abs(value) >= 100.0D) {
            return String.format(Locale.ROOT, "%.0f%s", value, suffix);
        }

        if (Math.abs(value) >= 10.0D) {
            return String.format(Locale.ROOT, "%.1f%s", value, suffix);
        }

        return String.format(Locale.ROOT, "%.2f%s", value, suffix);
    }
}
