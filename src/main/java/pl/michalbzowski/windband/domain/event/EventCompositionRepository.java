package pl.michalbzowski.windband.domain.event;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface EventCompositionRepository extends JpaRepository<EventComposition, Long> {

    /** All setlist rows for {@code eventId}, ordered by the setlist position. */
    List<EventComposition> findAllByEventIdOrderByOrderInSetAsc(Long eventId);

    /**
     * Same rows, with the LAZY {@link #composition} association already attached — JOIN FETCH —
     * so template rendering (open-in-view off) may read {@code ec.composition.title} without a live session.
     */
    @Query("select ec from EventComposition ec join fetch ec.composition " +
           "where ec.event.id = :eventId order by ec.orderInSet asc")
    List<EventComposition> findAllWithCompositionByEventId(Long eventId);

    Optional<EventComposition> findByEventIdAndCompositionId(Long eventId, Long compositionId);

    void deleteAllByEventId(Long eventId);
}
