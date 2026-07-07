package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.List;
import java.util.Optional;

public interface SpringDataInstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findByName(String name);

    List<Instrument> findAllByOrderBySortPriorityAsc();

    @Query("SELECT i FROM Instrument i WHERE i.band.id = :bandId OR i.band IS NULL ORDER BY i.sortPriority ASC, i.name ASC")
    List<Instrument> findAllByBandIdOrderBySortPriorityAsc(Long bandId);

    @Query("SELECT i FROM Instrument i WHERE i.name = :name AND i.band.id = :bandId")
    Optional<Instrument> findByNameAndBandId(String name, Long bandId);

    @Query("SELECT i FROM Instrument i WHERE i.id = :id AND i.band.id = :bandId")
    Optional<Instrument> findByIdAndBandId(Long id, Long bandId);
}
