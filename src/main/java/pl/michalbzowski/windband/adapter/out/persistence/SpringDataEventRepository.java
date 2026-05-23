package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.event.BandEvent;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataEventRepository extends JpaRepository<BandEvent, Long> {

    List<BandEvent> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<BandEvent> findAllByOrderByDateDesc();
}
