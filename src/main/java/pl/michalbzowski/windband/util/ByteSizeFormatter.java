package pl.michalbzowski.windband.util;

import java.util.Locale;

/**
 * Pure, dependency-free helper that renders a byte count as a compact, human-readable size
 * suitable for list rows (e.g. "1.4 MB", "512 B"). Lives in a neutral top-level {@code util}
 * package on purpose: it has no Spring / web / domain dependencies, so ArchUnit layering rules
 * stay green whichever layer reaches for it, and Thymeleaf templates can call it statically via
 * {@code T(pl.michalbzowski.windband.util.ByteSizeFormatter).human(bytes)}.
 *
 * <p>Formatting is deterministic (locale-independent dot decimal separator) so the SAME seed value
 * always renders the SAME string — important for UI assertions. The scale/rounding intentionally
 * matches the in-page JS helper {@code formatBytes} (KB/MB with one decimal, trailing ".0" trimmed)
 * so server-rendered rows and client-appended rows agree visually.</p>
 */
public final class ByteSizeFormatter {

    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private ByteSizeFormatter() {
        // utility — no instances
    }

    /**
     * Render {@code bytes} human-readable: "512 B" below 1 KiB, otherwise the largest unit where the
     * value is at least 1 with one decimal place (trailing ".0" trimmed) — e.g. "15.3 KB", "2 MB", "9 GB".
     * Negative or null-ish inputs fall back to "—" so a bad seed never throws in the template.
     */
    public static String human(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        if (bytes < 1024L) {
            return String.format(Locale.ROOT, "%d B", bytes);
        }

        double value = bytes / 1024.0d;
        int unit = 1;
        while (value >= 1024.0d && unit < UNITS.length - 1) {
            value /= 1024.0d;
            unit++;
        }

        String number = String.format(Locale.ROOT, "%.1f", value);
        if (number.endsWith(".0")) {
            number = number.substring(0, number.length() - 2);
        }
        return number + " " + UNITS[unit];
    }
}
