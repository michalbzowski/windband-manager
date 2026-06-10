package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface InstrumentAttributeDefRepository {
    List<InstrumentAttributeDef> findByBand(Band band);
    Optional<InstrumentAttributeDef> findByBandAndName(Band band, String name);
    InstrumentAttributeDef save(InstrumentAttributeDef def);
    Optional<InstrumentAttributeDef> findById(Long id);
    void delete(InstrumentAttributeDef def);
}
