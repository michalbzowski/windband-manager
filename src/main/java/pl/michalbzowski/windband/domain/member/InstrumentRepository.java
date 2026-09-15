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

    /** US-1.2: all instruments that have the given instrument as their alias target. */
    List<Instrument> findByAliasOf(Instrument canonical);

    /** US-1.2: all root (non-alias) instruments of a band, ordered by sort priority then name. */
    List<Instrument> findRootInstrumentsByBandId(Long bandId);

    Optional<Instrument> findByNameAndBandId(String name, Long bandId);

    Optional<Instrument> findByIdAndBandId(Long id, Long bandId);
}
