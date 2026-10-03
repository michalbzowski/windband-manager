package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.stereotype.Component;
import pl.michalbzowski.windband.domain.event.EventPartDelivery;
import pl.michalbzowski.windband.domain.event.EventPartDeliveryRepository;

import java.util.List;

/**
 * JPA adapter for the {@link EventPartDeliveryRepository} domain port (US-6.6).
 * Append-only delegation to Spring Data — no update/delete surface, same style as US-7.11's
 * {@code PartShareTokenRepositoryAdapter}.
 */
@Component
public class EventPartDeliveryRepositoryAdapter implements EventPartDeliveryRepository {

    private final SpringDataEventPartDeliveryRepository springData;

    public EventPartDeliveryRepositoryAdapter(SpringDataEventPartDeliveryRepository springData) {
        this.springData = springData;
    }

    @Override
    public EventPartDelivery save(EventPartDelivery row) {
        return springData.save(row);
    }

    @Override
    public List<EventPartDelivery> findAllByEventIdOrderBySentAtDesc(Long eventId) {
        return springData.findAllByEventIdOrderBySentAtDesc(eventId);
    }
}
