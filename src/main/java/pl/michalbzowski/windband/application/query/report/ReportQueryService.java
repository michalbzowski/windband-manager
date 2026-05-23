package pl.michalbzowski.windband.application.query.report;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.member.MemberQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;

import java.time.LocalDate;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportQueryService {

    private final RehearsalQueryService rehearsalQuery;
    private final EventQueryService eventQuery;
    private final MemberQueryService memberQuery;

    public MonthlyReport generateMonthlyReport(YearMonth yearMonth) {
        LocalDate from = yearMonth.atDay(1);
        LocalDate to = yearMonth.atEndOfMonth();

        var rehearsals = rehearsalQuery.getRehearsalsBetween(from, to);
        var events = eventQuery.getEventsBetween(from, to);
        long total = memberQuery.getActiveMemberCount();
        long minors = memberQuery.getMinorCount();
        long seniors = memberQuery.getSeniorCount();

        return new MonthlyReport(from, rehearsals, events, total, minors, seniors);
    }
}
