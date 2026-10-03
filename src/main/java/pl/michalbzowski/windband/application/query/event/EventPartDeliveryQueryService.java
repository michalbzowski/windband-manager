package pl.michalbzowski.windband.application.query.event;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.michalbzowski.windband.application.command.event.EventNotFoundException;
import pl.michalbzowski.windband.application.dto.event.EventPartDeliveryHistoryDto.DeliveryBlock;
import pl.michalbzowski.windband.application.dto.event.EventPartDeliveryHistoryDto.DeliveryHistory;
import pl.michalbzowski.windband.application.dto.event.EventPartDeliveryHistoryDto.DeliveryRow;
import pl.michalbzowski.windband.application.query.band.BandQueryService;
import pl.michalbzowski.windband.domain.event.BandEvent;
import pl.michalbzowski.windband.domain.event.EventPartDelivery;
import pl.michalbzowski.windband.domain.event.EventPartDeliveryRepository;
import pl.michalbzowski.windband.domain.event.EventRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * US-6.6 — read model for an event's parts-delivery audit history ("Historia rozdań").
 *
 * <p>Read-only CQRS over the append-only {@link EventPartDelivery} table written by the US-6.3/6.4
 * delivery command service — one entry per musician × part decision in every run, so re-sends
 * accumulate as fresh blocks instead of overwriting. Same lazy-safety contract as US-6.2's
 * {@link EventCompositionPartsQueryService}: the entity is scalar-only (no lazy associations),
 * so {@code open-in-view: false} cannot bite on this path.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EventPartDeliveryQueryService {

    /** The one outcome the "delivered" counters count — mirrors the command service's vocabulary. */
    public static final String OUTCOME_DELIVERED = "DELIVERED";

    private final BandQueryService bandQueryService;
    private final EventRepository eventRepository;
    private final EventPartDeliveryRepository auditRepository;

    /**
     * Returns the delivery history of {@code eventId} in the context of {@code bandId}.
     *
     * <p>Guarantees (identical to US-6.2): unknown event id → 404 on every path; foreign band →
     * 409 (no other tenant's audit is ever read); unknown team id → 400; null team context → no
     * rows (the command side never writes without a valid band anyway).</p>
     */
    public DeliveryHistory historyForEvent(Long eventId, Long bandId) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId jest wymagane");
        }
        BandEvent event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));
        if (bandId != null) {
            bandQueryService.getRequiredBand(bandId); // unknown team → IAE → 400, nothing loaded yet
            Long eventBandId = event.getBand() == null ? null : event.getBand().getId();
            if (eventBandId != null && !eventBandId.equals(bandId)) {
                throw new IllegalStateException("Wydarzenie " + eventId + " nie należy do zespołu " + bandId);
            }
        }

        List<EventPartDelivery> rows = auditRepository.findAllByEventIdOrderBySentAtDesc(eventId);
        if (rows.isEmpty()) {
            return DeliveryHistory.empty(event.getId());
        }

        int deliveredTotal = 0;
        int skippedTotal = 0;
        List<DeliveryBlock> blocks = new ArrayList<>();
        Instant currentRun = null;
        List<DeliveryRow> currentRows = new ArrayList<>();
        for (EventPartDelivery row : rows) {
            DeliveryRow dto = toDto(row);
            if (OUTCOME_DELIVERED.equals(row.getOutcome())) {
                deliveredTotal++;
            } else {
                skippedTotal++;
            }
            if (currentRun == null || !currentRun.equals(row.getSentAt())) {
                if (!currentRows.isEmpty()) {
                    blocks.add(new DeliveryBlock(currentRun, currentRows));
                }
                currentRun = row.getSentAt();
                currentRows = new ArrayList<>();
            }
            currentRows.add(dto);
        }
        if (!currentRows.isEmpty()) {
            blocks.add(new DeliveryBlock(currentRun, currentRows));
        }
        return new DeliveryHistory(event.getId(), deliveredTotal, skippedTotal, blocks);
    }

    private static DeliveryRow toDto(EventPartDelivery row) {
        return new DeliveryRow(row.getId(), row.getDeliveredTo(), row.getRecipientEmail(),
                row.getPieceTitle(), row.getPartRole(), row.getPageFrom(), row.getPageTo(),
                row.getChannel(), row.getOutcome(), row.getReason(), row.getActor(), row.getSentAt());
    }
}
