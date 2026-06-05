package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.michalbzowski.windband.domain.rehearsal.Rehearsal;

import java.time.LocalDate;
import java.util.List;

public interface SpringDataRehearsalRepository extends JpaRepository<Rehearsal, Long> {

    @Query("SELECT r FROM Rehearsal r LEFT JOIN FETCH r.attendances WHERE r.date BETWEEN :from AND :to ORDER BY r.date DESC")
    List<Rehearsal> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);

    @Query("SELECT r FROM Rehearsal r LEFT JOIN FETCH r.attendances WHERE r.date BETWEEN :from AND :to AND r.band.id = :bandId ORDER BY r.date DESC")
    List<Rehearsal> findByDateBetweenAndBandIdOrderByDateDesc(LocalDate from, LocalDate to, Long bandId);

    @Query("SELECT DISTINCT r FROM Rehearsal r LEFT JOIN FETCH r.attendances ORDER BY r.date DESC")
    List<Rehearsal> findAllByOrderByDateDesc();

    @Query("SELECT r FROM Rehearsal r LEFT JOIN FETCH r.attendances WHERE r.band.id = :bandId ORDER BY r.date DESC")
    List<Rehearsal> findAllByBandIdOrderByDateDesc(Long bandId);
}
