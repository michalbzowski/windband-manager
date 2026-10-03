package pl.michalbzowski.windband.domain.event;

import java.util.List;

/**
 * US-6.6 — domain port for the append-only parts-delivery audit trail.
 *
 * <p>The command side only ever {@link #save}s a fresh decision row (append-only); the query side
 * reads one event's history newest-first. No update/delete contract exists on purpose — history
 * rows document what happened and are never amended.
 */
public interface EventPartDeliveryRepository {

    EventPartDelivery save(EventPartDelivery row);

    List<EventPartDelivery> findAllByEventIdOrderBySentAtDesc(Long eventId);
}
