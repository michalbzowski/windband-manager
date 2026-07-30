package pl.michalbzowski.windband.application.dto;

import java.time.LocalDate;
import java.time.LocalTime;

public record UpcomingItemDto(
        String kind,
        Long id,
        String title,
        String subtitle,
        LocalDate date,
        LocalTime startTime,
        String badge,
        String href,
        String icon,
        Integer attendancePercentage
) {
    // Builder-like factory for backwards compatibility
    public static UpcomingItemDto of(String kind, Long id, String title, String subtitle,
            LocalDate date, LocalTime startTime, String badge, String href, String icon) {
        return new UpcomingItemDto(kind, id, title, subtitle, date, startTime, badge, href, icon, null);
    }
}
