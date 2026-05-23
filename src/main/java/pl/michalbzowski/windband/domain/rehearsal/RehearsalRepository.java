package pl.michalbzowski.windband.domain.rehearsal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RehearsalRepository {

    Rehearsal save(Rehearsal rehearsal);

    Optional<Rehearsal> findById(Long id);

    List<Rehearsal> findByDateBetween(LocalDate from, LocalDate to);

    List<Rehearsal> findAllOrderByDateDesc();

    void delete(Rehearsal rehearsal);
}
