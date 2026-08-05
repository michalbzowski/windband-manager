package pl.michalbzowski.windband.application.query.attention;

import pl.michalbzowski.windband.application.dto.UpcomingItemDto;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.PaymentStatus;
import pl.michalbzowski.windband.domain.event.PaymentType;
import pl.michalbzowski.windband.domain.event.ParticipationResponse;

/**
 * Attention condition: past paid-split event with at least one confirmed
 * participant whose payment status is not {@link PaymentStatus#PAID}.
 *
 * Rule: Event date is in the past AND payment type is PAID_SPLIT
 *       AND at least one CONFIRMED participant has paymentStatus
 *       that is not PAID — that is, either PENDING (admin called
 *       recordPayment) or NOT_APPLICABLE (admin never registered a
 *       payment for this confirmed person at all).
 *
 * DECLINED and NO_RESPONSE participants are ignored: a person who
 * declined isn't on the hook for money, and a person who hasn't
 * responded yet isn't either.
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

        // Check: at least one CONFIRMED member whose payment is not yet PAID
        boolean hasUnpaidConfirmed = event.getParticipations().stream()
                .anyMatch(p -> p.getResponse() == ParticipationResponse.CONFIRMED
                        && p.getPaymentStatus() != PaymentStatus.PAID);

        if (!hasUnpaidConfirmed) {
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
