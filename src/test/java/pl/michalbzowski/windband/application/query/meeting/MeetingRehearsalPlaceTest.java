package pl.michalbzowski.windband.application.query.meeting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.application.query.event.EventQueryService;
import pl.michalbzowski.windband.application.query.rehearsal.RehearsalQueryService;
import pl.michalbzowski.windband.domain.band.Band;
import pl.michalbzowski.windband.domain.member.Member;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Issue #227 — on /meetings the "Miejsce" column of a rehearsal row showed only the
 * attendance counter. The template renders {@code m.subtitle} for that column and the
 * rehearsal branch of the query service filled it with "Obecność x/y", so the place the
 * user booked never appeared. Events were fine (they pass the location through).
 *
 * <p>Attendance stays in the subtitle — /meetings has no progress bar for it, unlike the
 * dashboard — but the place now leads.
 */
@ExtendWith(MockitoExtension.class)
class MeetingRehearsalPlaceTest {

    @Mock
    private RehearsalQueryService rehearsalQueryService;

    @Mock
    private EventQueryService eventQueryService;

    @InjectMocks
    private MeetingQueryService meetingQueryService;

    @Test
    void upcomingRehearsalSubtitleLeadsWithThePlace() {
        Long teamId = 1L;
        when(rehearsalQueryService.getUpcomingRehearsals(teamId))
                .thenReturn(List.of(rehearsal(1L, LocalDate.now().plusDays(1), "Sala prób na dole")));
        when(eventQueryService.getUpcomingEvents(teamId)).thenReturn(List.of());

        UpcomingItemDto item = meetingQueryService.getUpcomingMeetings(teamId).get(0);

        assertThat(item.subtitle())
                .as("issue #227: the Miejsce column must show where the rehearsal happens")
                .startsWith("Sala prób na dole")
                .contains("Obecność 1/1");
    }

    @Test
    void pastRehearsalSubtitleLeadsWithThePlace() {
        Long teamId = 1L;
        when(rehearsalQueryService.getPastRehearsals(teamId))
                .thenReturn(List.of(rehearsal(2L, LocalDate.now().minusDays(3), "Remiza")));
        when(eventQueryService.getPastEvents(teamId)).thenReturn(List.of());

        UpcomingItemDto item = meetingQueryService.getPastMeetings(teamId).get(0);

        assertThat(item.subtitle()).startsWith("Remiza").contains("Obecność 1/1");
    }

    @Test
    void rehearsalWithoutPlaceStillReportsAttendance() {
        Long teamId = 1L;
        when(rehearsalQueryService.getUpcomingRehearsals(teamId))
                .thenReturn(List.of(rehearsal(3L, LocalDate.now().plusDays(2), "   ")));
        when(eventQueryService.getUpcomingEvents(teamId)).thenReturn(List.of());

        UpcomingItemDto item = meetingQueryService.getUpcomingMeetings(teamId).get(0);

        assertThat(item.subtitle())
                .as("no place to show must not turn the cell empty")
                .isEqualTo("Obecność 1/1");
    }

    private Rehearsal rehearsal(Long id, LocalDate date, String location) {
        Band band = Band.create("Test Band", "test-band");
        Rehearsal r = Rehearsal.schedule(date, LocalTime.of(18, 0), location, band);
        try {
            var field = Rehearsal.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(r, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        Member m = Member.create("Jan", "Kowalski", LocalDate.now().minusYears(20), band);
        r.recordAttendance(m, AttendanceStatus.PRESENT);
        return r;
    }
}
