package pl.michalbzowski.windband.domain.member;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {

    Instrument save(Instrument instrument);

    Optional<Instrument> findById(Long id);

    List<Instrument> findAll();

    Optional<Instrument> findByName(String name);
}
