package pl.michalbzowski.windband.application.query.attention;

import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.PaymentStatus;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.event.ParticipationResponse;

/**
 * Attention condition: past paid-split event with unpaid confirmed participations.
 *
 * Rule: Event date is in the past AND payment type is PAID_SPLIT
 *       AND at least one confirmed participant has payment status PENDING.
 */
public class PastPaidSplitUnpaidCondition implements AttentionCondition {

    private final BandEvent event;

    public PastPaidSplitUnpaidCondition(BandEvent event) {
        this.event = event;
    }

    @Override
    public UpcomingItemDto evaluate() {
        // Check: past event
        if (event.getDate().isAfter(java.time.LocalDate.now())) {
            return null;
        }

        // Check: PAID_SPLIT payment type
        if (event.getPaymentType() != PaymentType.PAID_SPLIT) {
            return null;
        }

        // Check: at least one confirmed member with PENDING payment
        boolean hasUnpaid = event.getParticipations().stream()
                .anyMatch(p -> p.getResponse() == ParticipationResponse.CONFIRMED
                        && p.getPaymentStatus() == PaymentStatus.PENDING);

        if (!hasUnpaid) {
            return null;
        }

        // Condition met → return UpcomingItemDto
        return new UpcomingItemDto(
                "ATTENTION_PAYMENT",
                event.getId(),
                event.getName(),
                "Wypłata nie została rozdysponowana",
                event.getDate(),
                null,
                "Uwaga",
                "/events/" + event.getId(),
                "🚨",
                null
        );
    }
}
