package pl.michalbzowski.windband.domain.inventory;

import pl.michalbzowski.windband.domain.band.Band;

import java.util.List;
import java.util.Optional;

public interface InstrumentAttributeDefRepository {
    InstrumentAttributeDef save(InstrumentAttributeDef def);
    List<InstrumentAttributeDef> findByBand(Band band);
    Optional<InstrumentAttributeDef> findById(Long id);
    void delete(InstrumentAttributeDef def);
}
