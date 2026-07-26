package pl.michalbzowski.windband.application.query.meeting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventType;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeetingQueryServiceTest {

    @Mock
    private RehearsalQueryService rehearsalQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @InjectMocks
    private MeetingQueryService meetingQueryService;

    @Test
    void getUpcomingMeetings_mergesRehearsalsAndEvents_sortedByDateThenTime() {
        // Given
        Long teamId = 1L;
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        // Rehearsal tomorrow 18:00
        Rehearsal r1 = createRehearsal(1L, tomorrow, LocalTime.of(18, 0), "Sala A");
        // Event today 19:00
        BandEvent e1 = createEvent(10L, "Koncert", today, LocalTime.of(19, 0), "Rynek", EventType.CONCERT, PaymentType.FREE);
        // Rehearsal today 17:00
        Rehearsal r2 = createRehearsal(2L, today, LocalTime.of(17, 0), "Sala B");

        when(rehearsalQueryService.getUpcomingRehearsals(teamId)).thenReturn(List.of(r1, r2));
        when(eventQueryService.getUpcomingEvents(teamId)).thenReturn(List.of(e1));

        // When
        List<UpcomingItemDto> result = meetingQueryService.getUpcomingMeetings(teamId);

        // Then - sorted by date asc, then time asc
        assertThat(result).hasSize(3);
        assertThat(result.get(0).kind()).isEqualTo("REHEARSAL");
        assertThat(result.get(0).title()).isEqualTo("Próba");
        assertThat(result.get(0).date()).isEqualTo(today);
        assertThat(result.get(0).startTime()).isEqualTo(LocalTime.of(17, 0));

        assertThat(result.get(1).kind()).isEqualTo("EVENT");
        assertThat(result.get(1).title()).isEqualTo("Koncert");
        assertThat(result.get(1).date()).isEqualTo(today);
        assertThat(result.get(1).startTime()).isEqualTo(LocalTime.of(19, 0));

        assertThat(result.get(2).kind()).isEqualTo("REHEARSAL");
        assertThat(result.get(2).date()).isEqualTo(tomorrow);
    }

    @Test
    void getPastMeetings_mergesAndSortsDesc() {
        // Given
        Long teamId = 1L;
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        LocalDate twoDaysAgo = today.minusDays(2);

        Rehearsal r1 = createRehearsal(1L, yesterday, LocalTime.of(18, 0), "Sala A");
        BandEvent e1 = createEvent(10L, "Koncert", twoDaysAgo, LocalTime.of(19, 0), "Rynek", EventType.CONCERT, PaymentType.FREE);
        Rehearsal r2 = createRehearsal(2L, twoDaysAgo, LocalTime.of(17, 0), "Sala B");

        when(rehearsalQueryService.getPastRehearsals(teamId)).thenReturn(List.of(r1, r2));
        when(eventQueryService.getPastEvents(teamId)).thenReturn(List.of(e1));

        // When
        List<UpcomingItemDto> result = meetingQueryService.getPastMeetings(teamId);

        // Then - sorted by date desc (most recent first), then time ASC (earlier first)
        assertThat(result).hasSize(3);
        assertThat(result.get(0).date()).isEqualTo(yesterday);
        assertThat(result.get(1).date()).isEqualTo(twoDaysAgo);
        assertThat(result.get(1).startTime()).isEqualTo(LocalTime.of(17, 0)); // rehearsal first (earlier time)
        assertThat(result.get(2).date()).isEqualTo(twoDaysAgo);
        assertThat(result.get(2).startTime()).isEqualTo(LocalTime.of(19, 0)); // event second (later time)
    }

    private Rehearsal createRehearsal(Long id, LocalDate date, LocalTime time, String location) {
        Band band = Band.create("Test Band", "test-band");
        Rehearsal r = Rehearsal.schedule(date, time, location, band);
        // Set ID via reflection since it's package-private
        setId(r, id);
        // Add dummy attendances for subtitle
        Member m = Member.create("Jan", "Kowalski", LocalDate.now().minusYears(20), band);
        setId(m, 1L);
        r.recordAttendance(m, AttendanceStatus.PRESENT);
        return r;
    }

    private BandEvent createEvent(Long id, String name, LocalDate date, LocalTime time,
                                   String location, EventType type, PaymentType paymentType) {
        Band band = Band.create("Test Band", "test-band");
        BandEvent e = BandEvent.create(name, date, time, location, type, band, paymentType, BigDecimal.ZERO);
        setId(e, id);
        return e;
    }

    private void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}