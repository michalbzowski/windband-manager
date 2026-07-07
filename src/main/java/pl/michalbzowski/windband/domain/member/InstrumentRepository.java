package pl.michalbzowski.windband.domain.member;

import java.util.List;
import java.util.Optional;

public interface InstrumentRepository {

    Instrument save(Instrument instrument);

    void delete(Instrument instrument);

    Optional<Instrument> findById(Long id);

    List<Instrument> findAll();

    List<Instrument> findAllOrderBySortPriority();

    List<Instrument> findAllOrderBySortPriorityByBandId(Long bandId);

    Optional<Instrument> findByName(String name);

    Optional<Instrument> findByNameAndBandId(String name, Long bandId);

    Optional<Instrument> findByIdAndBandId(Long id, Long bandId);
}
