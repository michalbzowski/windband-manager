package pl.michalbzowski.windband.application.query.meeting;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingQueryService {

    private final RehearsalQueryService rehearsalQueryService;
    private final EventQueryService eventQueryService;

    public List<UpcomingItemDto> getUpcomingMeetings(Long teamId) {
        LocalDate today = LocalDate.now();
        List<UpcomingItemDto> items = new ArrayList<>();

        // Rehearsals
        for (Rehearsal r : rehearsalQueryService.getUpcomingRehearsals(teamId)) {
            long present = r.getPresentCount();
            long total = r.getAttendances().size();
            Integer attendancePercentage = total > 0 ? (int) Math.round((present * 100.0) / total) : 0;
            items.add(new UpcomingItemDto(
                    "REHEARSAL",
                    r.getId(),
                    "Próba",
                    "Obecność " + present + "/" + total,
                    r.getDate(),
                    r.getStartTime(),
                    r.getDate().isEqual(today) ? "Dziś" : formatRelativeLabel(r.getDate(), today),
                    "/rehearsals/" + r.getId(),
                    "🎵",
                    attendancePercentage
            ));
        }

        // Events
        for (BandEvent e : eventQueryService.getUpcomingEvents(teamId)) {
            String subtitle = (e.getLocation() != null && !e.getLocation().isBlank())
                    ? e.getLocation() : e.getEventType().name();
            String badge;
            String paymentType = e.getPaymentType().name();
            if ("FREE".equals(paymentType)) {
                badge = "🎪 Koncert bezpłatny";
            } else if ("PAID_SPLIT".equals(paymentType)) {
                badge = "💰 Koncert płatny (podział)";
            } else { // PAID_TO_TEAM
                badge = "💰 Koncert płatny (kasa zespołu)";
            }
            items.add(new UpcomingItemDto(
                    "EVENT",
                    e.getId(),
                    e.getName(),
                    subtitle,
                    e.getDate(),
                    e.getStartTime(),
                    badge,
                    "/events/" + e.getId(),
                    "🎪",
                    null
            ));
        }

        return items.stream()
                .sorted(Comparator
                        .comparing(UpcomingItemDto::date)
                        .thenComparing(item -> item.startTime() != null ? item.startTime() : java.time.LocalTime.MAX)
                        .thenComparing(UpcomingItemDto::title))
                .toList();
    }

    public List<UpcomingItemDto> getPastMeetings(Long teamId) {
        LocalDate today = LocalDate.now();
        List<UpcomingItemDto> items = new ArrayList<>();

        // Rehearsals
        for (Rehearsal r : rehearsalQueryService.getPastRehearsals(teamId)) {
            long present = r.getPresentCount();
            long total = r.getAttendances().size();
            Integer attendancePercentage = total > 0 ? (int) Math.round((present * 100.0) / total) : 0;
            items.add(new UpcomingItemDto(
                    "REHEARSAL",
                    r.getId(),
                    "Próba",
                    "Obecność " + present + "/" + total,
                    r.getDate(),
                    r.getStartTime(),
                    formatRelativeLabel(r.getDate(), today),
                    "/rehearsals/" + r.getId(),
                    "🎵",
                    attendancePercentage
            ));
        }

        // Events
        for (BandEvent e : eventQueryService.getPastEvents(teamId)) {
            String subtitle = (e.getLocation() != null && !e.getLocation().isBlank())
                    ? e.getLocation() : e.getEventType().name();
            items.add(new UpcomingItemDto(
                    "EVENT",
                    e.getId(),
                    e.getName(),
                    subtitle,
                    e.getDate(),
                    e.getStartTime(),
                    formatRelativeLabel(e.getDate(), today),
                    "/events/" + e.getId(),
                    "🎪",
                    null
            ));
        }

        return items.stream()
                .sorted(Comparator
                        .comparing(UpcomingItemDto::date).reversed()
                        .thenComparing(item -> item.startTime() != null ? item.startTime() : java.time.LocalTime.MAX)
                        .thenComparing(UpcomingItemDto::title))
                .toList();
    }

    private String formatRelativeLabel(LocalDate date, LocalDate from) {
        long days = java.time.temporal.ChronoUnit.DAYS.between(from, date);
        if (days <= 0) return "Dziś";
        if (days == 1) return "Jutro";
        if (days < 7) return "Za " + days + " dni";
        return date.toString();
    }
}