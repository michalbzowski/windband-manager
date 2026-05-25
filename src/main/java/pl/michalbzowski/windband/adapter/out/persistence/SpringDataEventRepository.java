package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.michalbzowski.windband.domain.event.BandEvent;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataEventRepository extends JpaRepository<BandEvent, Long> {

    @Query("SELECT e FROM BandEvent e LEFT JOIN FETCH e.participations WHERE e.date BETWEEN :from AND :to ORDER BY e.date DESC")
    List<BandEvent> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    @Query("SELECT DISTINCT e FROM BandEvent e LEFT JOIN FETCH e.participations ORDER BY e.date DESC")
    List<BandEvent> findAllByOrderByDateDesc();
}
