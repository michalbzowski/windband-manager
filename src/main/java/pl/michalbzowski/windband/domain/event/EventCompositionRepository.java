package pl.michalbzowski.windband.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventCompositionRepository extends JpaRepository<EventComposition, Long> {

    /** All setlist rows for {@code eventId}, ordered by the setlist position. */
    List<EventComposition> findAllByEventIdOrderByOrderInSetAsc(Long eventId);

    Optional<EventComposition> findByEventIdAndCompositionId(Long eventId, Long compositionId);

    void deleteAllByEventId(Long eventId);
}
