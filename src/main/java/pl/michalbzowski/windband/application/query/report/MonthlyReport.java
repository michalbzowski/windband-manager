package pl.michalbzowski.windband.application.query.report;

import lombok.Getter;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.util.List;

@Getter
public class MonthlyReport {

    private final LocalDate reportMonth;
    private final int rehearsalCount;
    private final List<Rehearsal> rehearsals;
    private final int eventCount;
    private final List<BandEvent> events;
    private final long totalMembers;
    private final long minorCount;
    private final long seniorCount;

    public MonthlyReport(LocalDate reportMonth,
                         List<Rehearsal> rehearsals,
                         List<BandEvent> events,
                         long totalMembers, long minorCount, long seniorCount) {
        this.reportMonth = reportMonth;
        this.rehearsals = rehearsals;
        this.rehearsalCount = rehearsals.size();
        this.events = events;
        this.eventCount = events.size();
        this.totalMembers = totalMembers;
        this.minorCount = minorCount;
        this.seniorCount = seniorCount;
    }
}
