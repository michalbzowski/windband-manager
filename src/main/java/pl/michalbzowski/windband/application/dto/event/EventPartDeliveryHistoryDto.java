package pl.michalbzowski.windband.application.dto.event;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * US-6.6 — the read model for an event's parts-delivery history ("Historia rozdań").
 *
 * <p>One {@link DeliveryRow} is one musician × part decision in ONE run — exactly the audit
 * entity projected to lazy-safe scalars (the entity itself already is scalar-only: no lazy
 * associations, so {@code open-in-view: false} never bites on this path). The
 * {@link DeliveryHistory} envelope carries the event context the UI panel needs, newest run
 * first — re-sends accumulate as separate blocks, never overwrite. Timestamps are ISO-8601
 * instants on the wire (an {@code Instant} cannot be forced into a fixed "y"-pattern locally);
 * the UI formats them for display.</p>
 */
public final class EventPartDeliveryHistoryDto {

    private EventPartDeliveryHistoryDto() { }

    /** One audit row: who got which part in which run, with the honest outcome. */
    public record DeliveryRow(
            long id,
            String deliveredTo,
            String recipientEmail,
            String pieceTitle,
            String partRole,
            Integer pageFrom,
            Integer pageTo,
            String channel,
            String outcome,
            String reason,
            String actor,
            Instant sentAt) {
        public DeliveryRow {
            Objects.requireNonNull(deliveredTo, "deliveredTo required");
            Objects.requireNonNull(outcome, "outcome required");
            Objects.requireNonNull(sentAt, "sentAt required");
        }
    }

    /** One delivery run's worth of rows (same {@code sentAt} block in normal operation). */
    public record DeliveryBlock(Instant runAt, List<DeliveryRow> rows) {
        public DeliveryBlock {
            Objects.requireNonNull(runAt, "runAt required");
            rows = List.copyOf(rows);
        }
    }

    /** The event's full history: runs newest-first, each row within a run in decision order. */
    public record DeliveryHistory(long eventId, int totalDeliveredParts,
                                  int totalSkippedParts, List<DeliveryBlock> runs) {
        public DeliveryHistory {
            Objects.requireNonNull(runs, "runs required");
            runs = List.copyOf(runs);
        }

        public static DeliveryHistory empty(long eventId) { return new DeliveryHistory(eventId, 0, 0, List.of()); }
    }
}
