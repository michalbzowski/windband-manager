package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataRehearsalRepository extends JpaRepository<Rehearsal, Long> {

    List<Rehearsal> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    List<Rehearsal> findAllByOrderByDateDesc();
}
