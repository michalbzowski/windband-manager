package pl.michalbzowski.windband.application.query.attention;

import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;

/**
 * Attention condition: past rehearsal with unconfirmed location.
 *
 * Rule: Rehearsal date is in the past AND location is null or blank.
 */
public class PastRehearsalLocationUnconfirmedCondition implements AttentionCondition {

    private final Rehearsal rehearsal;

    public PastRehearsalLocationUnconfirmedCondition(Rehearsal rehearsal) {
        this.rehearsal = rehearsal;
    }

    @Override
    public UpcomingItemDto evaluate() {
        // Check: is the rehearsal in the past?
        if (rehearsal.getDate().isAfter(LocalDate.now())) {
            return null;
        }

        // Check: is location missing?
        if (rehearsal.getLocation() != null && !rehearsal.getLocation().isBlank()) {
            return null;
        }

        // Condition met → return UpcomingItemDto
        return new UpcomingItemDto(
                "ATTENTION_REHEARSAL_LOCATION",
                rehearsal.getId(),
                "Próba",
                "Brak potwierdzonej lokalizacji — członkowie nie wiedzą gdzie się zebrać",
                rehearsal.getDate(),
                rehearsal.getStartTime(),
                "Brak info",
                "/rehearsals/" + rehearsal.getId(),
                "📍",
                null
        );
    }
}