package pl.michalbzowski.windband.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import pl.michalbzowski.windband.domain.member.Instrument;

import java.util.Optional;

public interface SpringDataInstrumentRepository extends JpaRepository<Instrument, Long> {

    Optional<Instrument> findByName(String name);
}
