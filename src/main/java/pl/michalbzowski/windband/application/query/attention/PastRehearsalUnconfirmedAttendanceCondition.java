package pl.michalbzowski.windband.application.query.attention;

import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.domain.rehearsal.AttendanceStatus;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;

/**
 * Attention condition: past rehearsal with at least one member having unconfirmed attendance (NO_RESPONSE).
 *
 * Rule: Rehearsal date is in the past AND at least one invited member has attendance status NO_RESPONSE.
 */
public class PastRehearsalUnconfirmedAttendanceCondition implements AttentionCondition {

    private final Rehearsal rehearsal;

    public PastRehearsalUnconfirmedAttendanceCondition(Rehearsal rehearsal) {
        this.rehearsal = rehearsal;
    }

    @Override
    public UpcomingItemDto evaluate() {
        // Check: is the rehearsal in the past?
        if (rehearsal.getDate().isAfter(LocalDate.now())) {
            return null;
        }

        // Check: is there at least one invited member with NO_RESPONSE attendance?
        boolean hasUnconfirmed = rehearsal.getAttendances().stream()
                .anyMatch(a -> a.getStatus() == AttendanceStatus.NO_RESPONSE);

        if (!hasUnconfirmed) {
            return null;
        }

        // Condition met → return UpcomingItemDto
        return new UpcomingItemDto(
                "ATTENTION_REHEARSAL_ATTENDANCE",
                rehearsal.getId(),
                "Próba",
                "Niepodejmowana decyzja o obecności (brak odpowiedzi)",
                rehearsal.getDate(),
                rehearsal.getStartTime(),
                "Uwaga",
                "/rehearsals/" + rehearsal.getId(),
                "🎵",
                null
        );
    }
}