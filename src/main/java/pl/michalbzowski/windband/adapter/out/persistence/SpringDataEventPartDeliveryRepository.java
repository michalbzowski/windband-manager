package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.event.EventPartDelivery;

import java.util.List;

/**
 * Spring Data adapter for the US-6.6 delivery-audit entity (append-only).
 * The history read is one indexed query per event, newest first — scalar columns only, no
 * lazy association to fetch (US-6.4's LAZY-pitfall class of bug avoided by construction).
 */
public interface SpringDataEventPartDeliveryRepository extends JpaRepository<EventPartDelivery, Long> {

    List<EventPartDelivery> findAllByEventIdOrderBySentAtDesc(Long eventId);
}
