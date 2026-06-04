package pl.michalbzowski.windband.domain.event;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventRepository {

    BandEvent save(BandEvent event);

    Optional<BandEvent> findById(Long id);

    List<BandEvent> findByDateBetween(LocalDate from, LocalDate to);

    List<BandEvent> findByDateBetweenAndBandId(LocalDate from, LocalDate to, Long bandId);

    List<BandEvent> findAllOrderByDateDesc();

    List<BandEvent> findAllOrderByDateDescByBandId(Long bandId);

    void delete(BandEvent event);
}
